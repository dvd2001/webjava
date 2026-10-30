package hu.kerekpar.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBikeRepository extends JpaRepository<BikeJpaEntity, Long> {

    List<BikeJpaEntity> findAllByOrderByIdAsc();

    @Query("select count(b) > 0 from BikeJpaEntity b where lower(b.name) = lower(:name)")
    boolean existsByNameIgnoringCase(@Param("name") String name);
}
