package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.application.port.in.ReturnBikeUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.User;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReturnBikeService implements ReturnBikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReturnBikeService.class);

    private final BikeRepository bikes;
    private final RentalRepository rentals;
    private final TariffRepository tariffs;
    private final ClockPort clock;
    private final UnitOfWork unitOfWork;
    private final ActorGuard guard;
    private final RentalViewFactory views;

    public ReturnBikeService(BikeRepository bikes, RentalRepository rentals, UserRepository users,
                             TariffRepository tariffs, ClockPort clock, UnitOfWork unitOfWork) {
        this.bikes = bikes;
        this.rentals = rentals;
        this.tariffs = tariffs;
        this.clock = clock;
        this.unitOfWork = unitOfWork;
        this.guard = new ActorGuard(users);
        this.views = new RentalViewFactory(bikes, tariffs);
    }

    @Override
    public RentalView execute(Command command) {
        return unitOfWork.execute(() -> {
            User actor = guard.resolve(command.actorId());
            Rental rental = rentals.findById(command.rentalId())
                    .orElseThrow(() -> new NotFoundException("RENTAL_NOT_FOUND", "A bérlés nem található."));
            if (!actor.isManager() && !rental.userId().equals(actor.id())) {
                throw new ForbiddenException("NOT_RENTAL_OWNER", "Csak a saját bérlésedet zárhatod le.");
            }
            Bike bike = bikes.findById(rental.bikeId())
                    .orElseThrow(() -> new NotFoundException("BIKE_NOT_FOUND", "A bicikli nem található."));
            Tariff tariff = tariffs.findByType(bike.type())
                    .orElseThrow(() -> new NotFoundException("TARIFF_NOT_FOUND", "A tarifa nem található."));

            Instant now = clock.now();
            long fee = tariff.feeFor(rental.startedAt(), now);
            Rental finishedRental = rental.finish(now, fee);
            Bike returnedBike = bike.giveBack();
            Rental finished = rentals.save(finishedRental);
            bikes.save(returnedBike);
            log.info("Bicikli visszavéve: rental={}, bike={}, actor={}, díj={} Ft",
                    finished.id(), bike.id(), actor.id(), fee);
            return views.create(finished, now);
        });
    }
}
