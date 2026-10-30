package hu.kerekpar.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Szándékosan hibás vagy hamisított tokenek a biztonsági tesztekhez. */
public final class TestTokens {

    /** A biztonsági tesztek közös, ismert JWT-kulcsa (legalább 32 bájt, csak tesztben használt). */
    public static final String SECRET = "test-only-signing-secret-0123456789-abcdef";
    public static final String ISSUER = "kerekpar-berles";

    private TestTokens() {
    }

    public static String signed(String secret, String issuer, String subject, String role,
                                Instant issuedAt, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).issuedAt(issuedAt).expiresAt(expiresAt);
        if (role != null) {
            claims.claim("role", role);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    /** Érvényes aláírású, a közös kulccsal kiadott token tetszőleges alanyra és szerepkörre. */
    public static String valid(String subject, String role) {
        Instant now = Instant.now();
        return signed(SECRET, ISSUER, subject, role, now, now.plusSeconds(600));
    }

    /** Aláírás nélküli ({@code alg: none}) token: a szerver soha nem fogadhatja el. */
    public static String unsigned(String subject, String role) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        String payload = encoder.encodeToString(("{\"iss\":\"" + ISSUER + "\",\"sub\":\"" + subject
                + "\",\"role\":\"" + role + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    /** A token tartalmának módosítása az aláírás újraszámolása nélkül. */
    public static String withTamperedPayload(String token, String newRole) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changed = payload.replaceAll("\"role\":\"[A-Z]+\"", "\"role\":\"" + newRole + "\"");
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(changed.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + encoded + "." + parts[2];
    }
}
