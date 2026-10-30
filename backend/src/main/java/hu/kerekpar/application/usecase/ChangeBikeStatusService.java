package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.ChangeBikeStatusUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeBikeStatusService implements ChangeBikeStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangeBikeStatusService.class);

    private final BikeRepository bikes;
    private final UnitOfWork unitOfWork;
    private final ActorGuard guard;

    public ChangeBikeStatusService(BikeRepository bikes, UserRepository users, UnitOfWork unitOfWork) {
        this.bikes = bikes;
        this.unitOfWork = unitOfWork;
        this.guard = new ActorGuard(users);
    }

    @Override
    public Bike execute(Command command) {
        return unitOfWork.execute(() -> {
            User actor = guard.resolveManager(command.actorId(), "Az állapotot csak az állomásvezető módosíthatja.");
            Bike bike = bikes.findById(command.bikeId())
                    .orElseThrow(() -> new NotFoundException("BIKE_NOT_FOUND", "A bicikli nem található."));
            Bike updated = bikes.save(bike.changeStatus(command.status()));
            log.info("Bicikli státusza módosítva: id={}, {} -> {}, actor={}",
                    bike.id(), bike.status(), updated.status(), actor.id());
            return updated;
        });
    }
}
