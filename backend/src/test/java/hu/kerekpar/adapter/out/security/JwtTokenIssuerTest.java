package hu.kerekpar.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import hu.kerekpar.application.port.out.TokenIssuer.IssuedToken;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import hu.kerekpar.support.TestTokens;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusMillis(789);
    private static final User PETER = new User("u2", "Nagy Péter", UserRole.RENTER);

    private final SecretKeySpec key = new SecretKeySpec(TestTokens.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
            () -> NOW, Duration.ofMinutes(30), "kerekpar-berles");

    private JwtDecoder decoder(SecretKeySpec decodingKey, String expectedIssuer, java.time.Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(decodingKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(expectedIssuer));
        return decoder;
    }

    @Test
    void tokenCarriesTheUserRoleAndLifetimeClaims() {
        IssuedToken token = issuer.issue(PETER);

        Jwt jwt = decoder(key, "kerekpar-berles", null).decode(token.value());
        Instant issuedAt = NOW.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        assertThat(jwt.getSubject()).isEqualTo("u2");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("RENTER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("kerekpar-berles");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plus(Duration.ofMinutes(30)));
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void tokenDoesNotContainPersonalOrSecretData() {
        String token = issuer.issue(PETER).value();
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(payload).doesNotContain("Nagy").doesNotContain("password").doesNotContain("hash");
    }

    @Test
    void everyTokenHasAUniqueId() {
        Jwt first = decoder(key, "kerekpar-berles", null).decode(issuer.issue(PETER).value());
        Jwt second = decoder(key, "kerekpar-berles", null).decode(issuer.issue(PETER).value());

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void tokenIsOnlyValidWithTheSigningKeyAndIssuer() {
        String token = issuer.issue(PETER).value();
        SecretKeySpec otherKey = new SecretKeySpec("masik-kulcs-masik-kulcs-masik-kulcs-123".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        assertThatThrownBy(() -> decoder(otherKey, "kerekpar-berles", null).decode(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder(key, "masik-kiallito", null).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenIssuedInThePastIsExpiredForTheDecoder() {
        JwtTokenIssuer oldIssuer = new JwtTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                () -> Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(30), "kerekpar-berles");

        assertThatThrownBy(() -> decoder(key, "kerekpar-berles", null).decode(oldIssuer.issue(PETER).value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void managerRoleIsEncodedAsManager() {
        Jwt jwt = decoder(key, "kerekpar-berles", null)
                .decode(issuer.issue(new User("u1", "Kovács Anna", UserRole.MANAGER)).value());

        assertThat(jwt.<String>getClaim(JwtTokenIssuer.ROLE_CLAIM)).isEqualTo("MANAGER");
    }
}
