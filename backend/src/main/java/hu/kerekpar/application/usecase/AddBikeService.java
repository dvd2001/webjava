package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.AddBikeUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddBikeService implements AddBikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(AddBikeService.class);

    private final BikeRepository bikes;
    private final UnitOfWork unitOfWork;
    private final ActorGuard guard;

    public AddBikeService(BikeRepository bikes, UserRepository users, UnitOfWork unitOfWork) {
        this.bikes = bikes;
        this.unitOfWork = unitOfWork;
        this.guard = new ActorGuard(users);
    }

    @Override
    public Bike execute(Command command) {
        return unitOfWork.execute(() -> {
            User actor = guard.resolveManager(command.actorId(), "Új biciklit csak az állomásvezető vehet fel.");
            String name = Bike.normalizeName(command.name());
            if (bikes.existsByName(name)) {
                log.warn("Bicikli felvétele elutasítva (név foglalt): name={}, actor={}", name, actor.id());
                throw new ConflictException("BIKE_NAME_TAKEN", "Már létezik ilyen nevű bicikli.");
            }
            Bike saved = bikes.save(Bike.register(bikes.nextId(), name, command.type()));
            log.info("Bicikli felvéve: id={}, name={}, type={}, actor={}",
                    saved.id(), saved.name(), saved.type(), actor.id());
            return saved;
        });
    }
}
