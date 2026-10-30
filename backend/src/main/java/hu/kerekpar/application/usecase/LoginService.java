package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.LoginUseCase;
import hu.kerekpar.application.port.out.CredentialsRepository;
import hu.kerekpar.application.port.out.CredentialsRepository.UserCredentials;
import hu.kerekpar.application.port.out.PasswordHasher;
import hu.kerekpar.application.port.out.TokenIssuer;
import hu.kerekpar.application.port.out.TokenIssuer.IssuedToken;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginService implements LoginUseCase {

    static final int MAX_USERNAME_LENGTH = 100;
    static final int MAX_PASSWORD_LENGTH = 128;

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final CredentialsRepository credentials;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;

    public LoginService(CredentialsRepository credentials, PasswordHasher hasher, TokenIssuer tokens) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Override
    public LoginResult execute(Command command) {
        String username = command.username() == null ? "" : command.username().trim().toLowerCase(Locale.ROOT);
        String password = command.password() == null ? "" : command.password();
        if (username.isEmpty() || username.length() > MAX_USERNAME_LENGTH
                || password.isEmpty() || password.length() > MAX_PASSWORD_LENGTH) {
            log.warn("Sikertelen bejelentkezés: érvénytelen bemenet, username={}", LogText.printable(username));
            throw invalidCredentials();
        }

        Optional<UserCredentials> found = credentials.findByUsername(username);
        String storedHash = found.map(UserCredentials::passwordHash).filter(h -> !h.isBlank()).orElse(null);
        // a hash-ellenőrzés mindig lefut, a hiányzó felhasználó nem gyorsabb válasz
        boolean passwordMatches = hasher.matches(password, storedHash == null ? hasher.fakeHash() : storedHash);
        if (found.isEmpty() || storedHash == null || !passwordMatches) {
            log.warn("Sikertelen bejelentkezés: username={}", LogText.printable(username));
            throw invalidCredentials();
        }

        IssuedToken token = tokens.issue(found.get().user());
        log.info("Sikeres bejelentkezés: user={}, role={}", found.get().user().id(), found.get().user().role());
        return new LoginResult(found.get().user(), token.value(), token.expiresAt());
    }

    private static UnauthenticatedException invalidCredentials() {
        return new UnauthenticatedException("INVALID_CREDENTIALS", "Hibás felhasználónév vagy jelszó.");
    }
}
