package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.CredentialsRepository;
import java.util.Optional;

class PostgresCredentialsRepository implements CredentialsRepository {

    private final SpringDataUserRepository jpa;

    PostgresCredentialsRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<UserCredentials> findByUsername(String username) {
        return jpa.findByUsername(username)
                .map(entity -> new UserCredentials(UserMapper.toDomain(entity), entity.getPasswordHash()));
    }
}
