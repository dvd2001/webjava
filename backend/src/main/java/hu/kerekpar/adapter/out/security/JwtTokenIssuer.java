package hu.kerekpar.adapter.out.security;

import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.TokenIssuer;
import hu.kerekpar.domain.model.User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Aláírt (HS256) JWT kiadása; a {@code sub} a felhasználó azonosítója, a {@code role} a szerepkör. */
public class JwtTokenIssuer implements TokenIssuer {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final ClockPort clock;
    private final Duration timeToLive;
    private final String issuer;

    public JwtTokenIssuer(JwtEncoder encoder, ClockPort clock, Duration timeToLive, String issuer) {
        this.encoder = encoder;
        this.clock = clock;
        this.timeToLive = timeToLive;
        this.issuer = issuer;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant issuedAt = clock.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(timeToLive);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.id())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, user.role().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
