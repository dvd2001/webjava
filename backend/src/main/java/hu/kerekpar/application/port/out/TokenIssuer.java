package hu.kerekpar.application.port.out;

import hu.kerekpar.domain.model.User;
import java.time.Instant;

public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
