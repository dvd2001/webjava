package hu.kerekpar.domain.model;

import hu.kerekpar.domain.exception.ConflictException;
import java.time.Instant;
import java.util.Objects;

/** Egy bérlés; {@code endedAt} és {@code fee} csak lezárt bérlésnél kitöltött. */
public record Rental(String id, String bikeId, String userId, Instant startedAt, Instant endedAt, Long fee) {

    public Rental {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bikeId, "bikeId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(startedAt, "startedAt");
    }

    public static Rental start(String id, String bikeId, String userId, Instant now) {
        return new Rental(id, bikeId, userId, now, null, null);
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public Rental finish(Instant now, long fee) {
        if (!isActive()) {
            throw new ConflictException("RENTAL_ALREADY_FINISHED", "A bérlés már lezárult.");
        }
        return new Rental(id, bikeId, userId, startedAt, now, fee);
    }
}
