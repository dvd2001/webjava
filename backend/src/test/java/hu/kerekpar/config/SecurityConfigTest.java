package hu.kerekpar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    private static SecurityProperties withSecret(String secret) {
        return new SecurityProperties(secret, "kerekpar-berles", Duration.ofMinutes(30), 4, 5, Duration.ofMinutes(15));
    }

    @Test
    void secretShorterThan32BytesFailsStartup() {
        assertThatThrownBy(() -> config.jwtSigningKey(withSecret("rovid-titok")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bájt");
    }

    @Test
    void configuredSecretIsUsedAsTheKey() {
        String secret = "0123456789-0123456789-0123456789-01";

        SecretKey key = config.jwtSigningKey(withSecret(secret));

        assertThat(key.getEncoded()).isEqualTo(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void missingSecretGeneratesARandomKeyEveryTime() {
        for (String blank : new String[] {null, "", "   "}) {
            SecretKey first = config.jwtSigningKey(withSecret(blank));
            SecretKey second = config.jwtSigningKey(withSecret(blank));

            assertThat(first.getEncoded()).hasSize(32).isNotEqualTo(second.getEncoded());
        }
    }

    @Test
    void propertiesHaveSafeDefaultsWhenNothingIsConfigured() {
        SecurityProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("kerekpar.security", SecurityProperties.class);

        assertThat(defaults.jwtSecret()).as("nincs beégetett kulcs").isNull();
        assertThat(defaults.issuer()).isEqualTo("kerekpar-berles");
        assertThat(defaults.tokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(defaults.bcryptStrength()).isGreaterThanOrEqualTo(10);
        assertThat(defaults.maxLoginFailures()).isEqualTo(5);
        assertThat(defaults.loginLockDuration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void propertiesAreBoundFromConfiguration() {
        SecurityProperties bound = new Binder(new MapConfigurationPropertySource(Map.of(
                "kerekpar.security.token-ttl", "PT5M",
                "kerekpar.security.max-login-failures", "3",
                "kerekpar.security.jwt-secret", "x".repeat(40))))
                .bindOrCreate("kerekpar.security", SecurityProperties.class);

        assertThat(bound.tokenTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(bound.maxLoginFailures()).isEqualTo(3);
        assertThat(bound.jwtSecret()).hasSize(40);
    }
}
