package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeType;

public interface AddBikeUseCase {

    Bike execute(Command command);

    record Command(String actorId, String name, BikeType type) {
    }
}
