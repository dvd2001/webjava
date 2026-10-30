package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.SearchBikesUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeSearchCriteria;
import hu.kerekpar.domain.model.User;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Csak olvas. A kérést indító felhasználónak léteznie kell (a token önmagában nem elég), a szűrés Java oldalon fut,
 * ezért az in-memory és az adatbázis adapter pontosan ugyanúgy viselkedik.
 */
public class SearchBikesService implements SearchBikesUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchBikesService.class);

    private static final Comparator<Bike> BY_NAME = Comparator
            .comparing((Bike bike) -> bike.name().toLowerCase(Locale.ROOT))
            .thenComparing(Bike::name)
            .thenComparing(Bike::id);

    private final BikeRepository bikes;
    private final ActorGuard guard;

    public SearchBikesService(BikeRepository bikes, UserRepository users) {
        this.bikes = bikes;
        this.guard = new ActorGuard(users);
    }

    @Override
    public SearchResult execute(Query query) {
        User actor = guard.resolve(query.actorId());
        BikeSearchCriteria criteria = BikeSearchCriteria.of(query.text(), query.type(), query.status(), query.limit());

        List<Bike> matched = bikes.findAll().stream().filter(criteria::matches).sorted(BY_NAME).toList();
        List<Bike> items = matched.size() > criteria.limit() ? matched.subList(0, criteria.limit()) : matched;

        log.info("Bicikli keresés: actor={}, q='{}', type={}, status={} -> {} találat ({} visszaadva)",
                actor.id(), LogText.printable(criteria.nameContains()), criteria.type(), criteria.status(),
                matched.size(), items.size());
        return new SearchResult(List.copyOf(items), matched.size());
    }
}
