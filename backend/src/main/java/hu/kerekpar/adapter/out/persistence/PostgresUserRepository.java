package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.User;
import java.util.List;
import java.util.Optional;

class PostgresUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    PostgresUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<User> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(UserMapper::toDomain).toList();
    }

    @Override
    public Optional<User> findById(String id) {
        return EntityIds.parse(EntityIds.USER, id).flatMap(jpa::findById).map(UserMapper::toDomain);
    }
}
