package hu.kerekpar.application.port.in;

public interface ReturnBikeUseCase {

    RentalView execute(Command command);

    record Command(String actorId, String rentalId) {
    }
}
