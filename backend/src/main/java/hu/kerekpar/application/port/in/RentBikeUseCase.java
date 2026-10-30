package hu.kerekpar.application.port.in;

public interface RentBikeUseCase {

    RentalView execute(Command command);

    record Command(String actorId, String bikeId) {
    }
}
