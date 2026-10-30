package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import java.util.List;

public interface SearchBikesUseCase {

    SearchResult execute(Query query);

    /** A feltételek nyers értékek; az ellenőrzésük a domain szabályai szerint történik. */
    record Query(String actorId, String text, BikeType type, BikeStatus status, Integer limit) {
    }

    /** @param total az összes találat száma a limit előtt */
    record SearchResult(List<Bike> items, int total) {
    }
}
