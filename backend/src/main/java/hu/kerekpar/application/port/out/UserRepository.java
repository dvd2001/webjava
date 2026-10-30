package hu.kerekpar.application.port.out;

import hu.kerekpar.domain.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    List<User> findAll();

    Optional<User> findById(String id);
}
