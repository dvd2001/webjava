package hu.kerekpar.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Biztonsági beállítások ({@code kerekpar.security.*}).
 *
 * @param jwtSecret      a JWT HMAC kulcsa (legalább 32 bájt); üresen hagyva minden indításkor új, véletlen kulcs készül
 *                       (a korábbi tokenek érvénytelenné válnak), ezért élesben környezeti változóból kell megadni
 * @param issuer         a tokenek kiállítója, az ellenőrzés is megköveteli
 * @param tokenTtl       a token élettartama
 * @param bcryptStrength a BCrypt költségtényezője
 * @param maxLoginFailures a sikertelen bejelentkezések száma a zárolásig
 * @param loginLockDuration a zárolás időtartama
 */
@ConfigurationProperties("kerekpar.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("kerekpar-berles") String issuer,
        @DefaultValue("PT30M") Duration tokenTtl,
        @DefaultValue("10") int bcryptStrength,
        @DefaultValue("5") int maxLoginFailures,
        @DefaultValue("PT15M") Duration loginLockDuration) {
}
