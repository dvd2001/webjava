package hu.kerekpar.application.port.out;

import hu.kerekpar.domain.model.User;
import java.util.Optional;

public interface CredentialsRepository {

    /** A felhasználónév kis- és nagybetűtől függetlenül egyedi; a megadott érték már normalizált (kisbetűs). */
    Optional<UserCredentials> findByUsername(String username);

    /** {@code passwordHash} null, ha a felhasználó nem tud bejelentkezni. */
    record UserCredentials(User user, String passwordHash) {
    }
}
