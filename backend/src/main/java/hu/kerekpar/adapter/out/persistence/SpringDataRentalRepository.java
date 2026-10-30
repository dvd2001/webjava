package hu.kerekpar.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRentalRepository extends JpaRepository<RentalJpaEntity, Long> {

    List<RentalJpaEntity> findAllByOrderByIdAsc();
}
