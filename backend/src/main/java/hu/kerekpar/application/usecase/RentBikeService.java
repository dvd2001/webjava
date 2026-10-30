package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.RentBikeUseCase;
import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.User;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RentBikeService implements RentBikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(RentBikeService.class);

    private final BikeRepository bikes;
    private final RentalRepository rentals;
    private final ClockPort clock;
    private final UnitOfWork unitOfWork;
    private final ActorGuard guard;
    private final RentalViewFactory views;

    public RentBikeService(BikeRepository bikes, RentalRepository rentals, UserRepository users,
                           TariffRepository tariffs, ClockPort clock, UnitOfWork unitOfWork) {
        this.bikes = bikes;
        this.rentals = rentals;
        this.clock = clock;
        this.unitOfWork = unitOfWork;
        this.guard = new ActorGuard(users);
        this.views = new RentalViewFactory(bikes, tariffs);
    }

    @Override
    public RentalView execute(Command command) {
        return unitOfWork.execute(() -> {
            User actor = guard.resolveRenter(command.actorId(), "Bérelni csak bérlőként lehet.");
            Bike bike = bikes.findById(command.bikeId())
                    .orElseThrow(() -> new NotFoundException("BIKE_NOT_FOUND", "A bicikli nem található."));
            Instant now = clock.now();
            bikes.save(bike.rent());
            Rental rental = rentals.save(Rental.start(rentals.nextId(), bike.id(), actor.id(), now));
            log.info("Bicikli kibérelve: rental={}, bike={}, user={}", rental.id(), bike.id(), actor.id());
            return views.create(rental, now);
        });
    }
}
