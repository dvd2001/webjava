package hu.kerekpar.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    List<UserJpaEntity> findAllByOrderByIdAsc();

    Optional<UserJpaEntity> findByUsername(String username);
}
