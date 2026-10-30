package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.model.Rental;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class PostgresRentalRepository implements RentalRepository {

    private static final String ONE_ACTIVE_PER_BIKE = "uq_rental_one_active_per_bike";

    private final SpringDataRentalRepository jpa;
    private final IdSequence ids;

    PostgresRentalRepository(SpringDataRentalRepository jpa, IdSequence ids) {
        this.jpa = jpa;
        this.ids = ids;
    }

    @Override
    public List<Rental> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(RentalMapper::toDomain).toList();
    }

    @Override
    public Optional<Rental> findById(String id) {
        return EntityIds.parse(EntityIds.RENTAL, id).flatMap(jpa::findById).map(RentalMapper::toDomain);
    }

    @Override
    public Rental save(Rental rental) {
        long id = EntityIds.require(EntityIds.RENTAL, rental.id());
        RentalJpaEntity entity = jpa.findById(id).map(existing -> {
            RentalMapper.applyTo(rental, existing);
            return existing;
        }).orElseGet(() -> RentalMapper.toNewEntity(rental));
        try {
            return RentalMapper.toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, ONE_ACTIVE_PER_BIKE)) {
                throw new ConflictException("BIKE_NOT_AVAILABLE", "Ez a bicikli jelenleg nem bérelhető.");
            }
            throw e;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("CONCURRENT_MODIFICATION",
                    "A bérlés közben módosult, kérlek próbáld újra.");
        }
    }

    @Override
    public String nextId() {
        return EntityIds.toDomain(EntityIds.RENTAL, ids.next(IdSequence.RENTAL_TABLE));
    }
}
