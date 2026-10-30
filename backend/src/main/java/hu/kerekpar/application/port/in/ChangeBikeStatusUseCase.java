package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;

public interface ChangeBikeStatusUseCase {

    Bike execute(Command command);

    record Command(String actorId, String bikeId, BikeStatus status) {
    }
}
