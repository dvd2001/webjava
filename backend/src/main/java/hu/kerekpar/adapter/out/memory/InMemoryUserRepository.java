package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import java.util.List;
import java.util.Optional;

/** Rögzített példafelhasználók; hitelesítés nincs, a felhasználóváltást a kliens kéri az azonosítóval. */
public class InMemoryUserRepository implements UserRepository {

    private final List<User> users = List.of(
            new User("u1", "Kovács Anna", UserRole.MANAGER),
            new User("u2", "Nagy Péter", UserRole.RENTER),
            new User("u3", "Szabó Eszter", UserRole.RENTER),
            new User("u4", "Tóth Gábor", UserRole.RENTER));

    @Override
    public List<User> findAll() {
        return users;
    }

    @Override
    public Optional<User> findById(String id) {
        return users.stream().filter(u -> u.id().equals(id)).findFirst();
    }
}
