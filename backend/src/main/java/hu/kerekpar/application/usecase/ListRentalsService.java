package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.ListRentalsUseCase;
import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class ListRentalsService implements ListRentalsUseCase {

    private final RentalRepository rentals;
    private final ClockPort clock;
    private final ActorGuard guard;
    private final RentalViewFactory views;

    public ListRentalsService(BikeRepository bikes, RentalRepository rentals, UserRepository users,
                              TariffRepository tariffs, ClockPort clock) {
        this.rentals = rentals;
        this.clock = clock;
        this.guard = new ActorGuard(users);
        this.views = new RentalViewFactory(bikes, tariffs);
    }

    @Override
    public List<RentalView> execute(Query query) {
        User actor = guard.resolve(query.actorId());
        Filter filter = query.filter() == null ? Filter.ALL : query.filter();
        Instant now = clock.now();
        return rentals.findAll().stream()
                .filter(r -> actor.isManager() || r.userId().equals(actor.id()))
                .filter(r -> matches(filter, r))
                .sorted(Comparator.comparing(Rental::startedAt))
                .map(r -> views.create(r, now))
                .toList();
    }

    private static boolean matches(Filter filter, Rental rental) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> rental.isActive();
            case FINISHED -> !rental.isActive();
        };
    }
}
