package hu.kerekpar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.application.port.in.LoginUseCase;
import hu.kerekpar.application.port.out.CredentialsRepository;
import hu.kerekpar.application.port.out.CredentialsRepository.UserCredentials;
import hu.kerekpar.application.port.out.PasswordHasher;
import hu.kerekpar.application.port.out.TokenIssuer;
import hu.kerekpar.application.port.out.TokenIssuer.IssuedToken;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

    private static final User PETER = new User("u2", "Nagy Péter", UserRole.RENTER);
    private static final Instant EXPIRY = Instant.parse("2026-03-14T10:00:00Z");

    /** Átlátható hash-hamisítvány: a hash a jelszóból látszik, és számoljuk a hívásokat. */
    private static final class FakeHasher implements PasswordHasher {
        final List<String> checkedHashes = new ArrayList<>();

        @Override
        public String hash(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            checkedHashes.add(passwordHash);
            return passwordHash.equals("hash:" + rawPassword);
        }

        @Override
        public String fakeHash() {
            return "hash:<soha-nem-egyezo>";
        }
    }

    private final FakeHasher hasher = new FakeHasher();
    private final List<User> issuedFor = new ArrayList<>();
    private Map<String, UserCredentials> store;
    private LoginUseCase login;

    @BeforeEach
    void setUp() {
        store = Map.of(
                "peter", new UserCredentials(PETER, "hash:titok"),
                "nopass", new UserCredentials(new User("u5", "Nincs Jelszó", UserRole.RENTER), null),
                "blankpass", new UserCredentials(new User("u6", "Üres Jelszó", UserRole.RENTER), " "));
        TokenIssuer tokens = user -> {
            issuedFor.add(user);
            return new IssuedToken("token-" + user.id(), EXPIRY);
        };
        login = new LoginService(username -> java.util.Optional.ofNullable(store.get(username)), hasher, tokens);
    }

    @Test
    void correctCredentialsIssueATokenForTheUser() {
        LoginUseCase.LoginResult result = login.execute(new LoginUseCase.Command("peter", "titok"));

        assertThat(result.user()).isEqualTo(PETER);
        assertThat(result.accessToken()).isEqualTo("token-u2");
        assertThat(result.expiresAt()).isEqualTo(EXPIRY);
        assertThat(issuedFor).containsExactly(PETER);
    }

    @Test
    void usernameIsTrimmedAndLowerCased() {
        assertThat(login.execute(new LoginUseCase.Command("  PeTeR ", "titok")).user()).isEqualTo(PETER);
    }

    @Test
    void wrongPasswordIsRejectedWithTheGenericMessageAndNoToken() {
        assertThatThrownBy(() -> login.execute(new LoginUseCase.Command("peter", "rossz")))
                .isInstanceOfSatisfying(UnauthenticatedException.class, e -> {
                    assertThat(e.code()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(e.getMessage()).isEqualTo("Hibás felhasználónév vagy jelszó.");
                });
        assertThat(issuedFor).isEmpty();
    }

    @Test
    void unknownUserGetsTheSameErrorAndStillRunsAHashCheck() {
        assertThatThrownBy(() -> login.execute(new LoginUseCase.Command("senki", "titok")))
                .isInstanceOfSatisfying(UnauthenticatedException.class, e -> {
                    assertThat(e.code()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(e.getMessage()).isEqualTo("Hibás felhasználónév vagy jelszó.");
                });
        assertThat(hasher.checkedHashes).as("időzítés kiegyenlítése").containsExactly(hasher.fakeHash());
        assertThat(issuedFor).isEmpty();
    }

    @Test
    void userWithoutStoredPasswordCannotLogInEvenWithAnyPassword() {
        for (String username : List.of("nopass", "blankpass")) {
            assertThatThrownBy(() -> login.execute(new LoginUseCase.Command(username, "hash:<soha-nem-egyezo>")))
                    .isInstanceOf(UnauthenticatedException.class);
        }
        assertThat(issuedFor).isEmpty();
    }

    @Test
    void missingBlankOrOversizedInputIsRejectedWithoutHashing() {
        String longName = "a".repeat(LoginService.MAX_USERNAME_LENGTH + 1);
        String longPassword = "p".repeat(LoginService.MAX_PASSWORD_LENGTH + 1);
        List<LoginUseCase.Command> bad = List.of(
                new LoginUseCase.Command(null, "titok"),
                new LoginUseCase.Command("peter", null),
                new LoginUseCase.Command("   ", "titok"),
                new LoginUseCase.Command("peter", ""),
                new LoginUseCase.Command(longName, "titok"),
                new LoginUseCase.Command("peter", longPassword));

        for (LoginUseCase.Command command : bad) {
            assertThatThrownBy(() -> login.execute(command)).isInstanceOf(UnauthenticatedException.class);
        }
        assertThat(hasher.checkedHashes).isEmpty();
        assertThat(issuedFor).isEmpty();
    }

    @Test
    void passwordAtTheMaximumLengthIsStillChecked() {
        Map<String, UserCredentials> big = Map.of("peter",
                new UserCredentials(PETER, "hash:" + "p".repeat(LoginService.MAX_PASSWORD_LENGTH)));
        LoginUseCase useCase = new LoginService(username -> java.util.Optional.ofNullable(big.get(username)), hasher,
                user -> new IssuedToken("t", EXPIRY));

        assertThat(useCase.execute(new LoginUseCase.Command("peter", "p".repeat(LoginService.MAX_PASSWORD_LENGTH))).user())
                .isEqualTo(PETER);
    }
}
