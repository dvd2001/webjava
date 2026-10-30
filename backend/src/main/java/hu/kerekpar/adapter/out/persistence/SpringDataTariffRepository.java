package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.BikeType;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTariffRepository extends JpaRepository<TariffJpaEntity, BikeType> {
}
