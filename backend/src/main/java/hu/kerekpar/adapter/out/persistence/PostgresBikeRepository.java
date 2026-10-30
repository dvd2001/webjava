package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.model.Bike;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class PostgresBikeRepository implements BikeRepository {

    private static final String UNIQUE_NAME = "uq_bike_name_lower";

    private final SpringDataBikeRepository jpa;
    private final IdSequence ids;

    PostgresBikeRepository(SpringDataBikeRepository jpa, IdSequence ids) {
        this.jpa = jpa;
        this.ids = ids;
    }

    @Override
    public List<Bike> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(BikeMapper::toDomain).toList();
    }

    @Override
    public Optional<Bike> findById(String id) {
        return EntityIds.parse(EntityIds.BIKE, id).flatMap(jpa::findById).map(BikeMapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpa.existsByNameIgnoringCase(name);
    }

    @Override
    public Bike save(Bike bike) {
        long id = EntityIds.require(EntityIds.BIKE, bike.id());
        BikeJpaEntity entity = jpa.findById(id).map(existing -> {
            BikeMapper.applyTo(bike, existing);
            return existing;
        }).orElseGet(() -> BikeMapper.toNewEntity(bike));
        try {
            return BikeMapper.toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, UNIQUE_NAME)) {
                throw new ConflictException("BIKE_NAME_TAKEN", "Már létezik ilyen nevű bicikli.");
            }
            throw e;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("CONCURRENT_MODIFICATION",
                    "A bicikli közben módosult, kérlek próbáld újra.");
        }
    }

    @Override
    public String nextId() {
        return EntityIds.toDomain(EntityIds.BIKE, ids.next(IdSequence.BIKE_TABLE));
    }
}
