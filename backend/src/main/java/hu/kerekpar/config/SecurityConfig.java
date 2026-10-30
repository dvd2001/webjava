package hu.kerekpar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import hu.kerekpar.adapter.in.web.security.ApiSecurityErrorHandlers;
import hu.kerekpar.adapter.in.web.security.LoginAttemptLimiter;
import hu.kerekpar.adapter.out.security.BcryptPasswordHasher;
import hu.kerekpar.adapter.out.security.JwtTokenIssuer;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.PasswordHasher;
import hu.kerekpar.application.port.out.TokenIssuer;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Hitelesítés és jogosultság. Az API állapotmentes (nincs session), a hitelesítés Bearer JWT-vel történik,
 * ezért a CSRF-védelem ki van kapcsolva (a böngésző nem küld automatikusan hitelesítő adatot).
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * A végpont–szerepkör szabályok egyetlen helyen. Aki nincs felsorolva, azt a {@code denyAll} elutasítja
     * (név szerint nem ismert végpont nem lehet véletlenül nyitott). A use case-ek a szerepkört és a tulajdonjogot
     * (pl. csak a saját bérlés zárható le) ezen felül, a domain szabályai szerint is ellenőrzik.
     */
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiSecurityErrorHandlers errorHandlers,
                                               JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/tariffs", "/api/bikes", "/api/bikes/search").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/bikes").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/bikes/*/status").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/rentals").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/rentals").hasRole("RENTER")
                        .requestMatchers(HttpMethod.POST, "/api/rentals/*/return").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers));
        return http.build();
    }

    @Bean
    ApiSecurityErrorHandlers apiSecurityErrorHandlers(ObjectMapper objectMapper) {
        return new ApiSecurityErrorHandlers(objectMapper);
    }

    /** A {@code role} claim (MANAGER / RENTER) a {@code ROLE_} előtagú jogosultsággá alakul. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenIssuer.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties properties) {
        byte[] secret;
        if (properties.jwtSecret() == null || properties.jwtSecret().isBlank()) {
            secret = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(secret);
            log.warn("A kerekpar.security.jwt-secret nincs megadva: véletlen kulcsot használok, "
                    + "újraindításkor a kiadott tokenek érvénytelenné válnak.");
        } else {
            secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "A kerekpar.security.jwt-secret legalább " + MIN_SECRET_BYTES + " bájt hosszú legyen.");
            }
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // lejárat és kiállító ellenőrzése
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    PasswordHasher passwordHasher(SecurityProperties properties) {
        return new BcryptPasswordHasher(properties.bcryptStrength());
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder jwtEncoder, ClockPort clock, SecurityProperties properties) {
        return new JwtTokenIssuer(jwtEncoder, clock, properties.tokenTtl(), properties.issuer());
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter(SecurityProperties properties, Clock clock) {
        return new LoginAttemptLimiter(properties.maxLoginFailures(), properties.loginLockDuration(), clock);
    }
}
