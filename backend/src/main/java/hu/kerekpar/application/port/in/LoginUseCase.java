package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.User;
import java.time.Instant;

public interface LoginUseCase {

    LoginResult execute(Command command);

    record Command(String username, String password) {
    }

    record LoginResult(User user, String accessToken, Instant expiresAt) {
    }
}
