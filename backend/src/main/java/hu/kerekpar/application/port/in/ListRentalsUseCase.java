package hu.kerekpar.application.port.in;

import java.util.List;

public interface ListRentalsUseCase {

    List<RentalView> execute(Query query);

    enum Filter {
        ALL,
        ACTIVE,
        FINISHED
    }

    record Query(String actorId, Filter filter) {
    }
}
