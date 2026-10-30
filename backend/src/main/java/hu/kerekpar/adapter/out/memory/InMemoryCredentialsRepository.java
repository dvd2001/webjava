package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.CredentialsRepository;
import hu.kerekpar.application.port.out.PasswordHasher;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.User;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fejlesztői, memóriabeli bejelentkezési adatok: minden példafelhasználónak ugyanaz a (konfigurált) demo jelszó. */
public class InMemoryCredentialsRepository implements CredentialsRepository {

    private static final Map<String, String> USERNAMES = Map.of(
            "u1", "anna",
            "u2", "peter",
            "u3", "eszter",
            "u4", "gabor");

    private final Map<String, UserCredentials> byUsername = new HashMap<>();

    public InMemoryCredentialsRepository(UserRepository users, PasswordHasher hasher, String demoPassword) {
        String hash = hasher.hash(demoPassword);
        for (User user : users.findAll()) {
            String username = USERNAMES.get(user.id());
            if (username != null) {
                byUsername.put(username, new UserCredentials(user, hash));
            }
        }
    }

    @Override
    public Optional<UserCredentials> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }
}
