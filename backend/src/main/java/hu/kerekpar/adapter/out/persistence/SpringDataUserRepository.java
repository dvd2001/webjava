package hu.kerekpar.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    List<UserJpaEntity> findAllByOrderByIdAsc();
}
