package hu.kerekpar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.model.Rental;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RentalTest {

    private static final Instant START = Instant.parse("2026-03-14T09:30:00Z");

    @Test
    void startedRentalIsActiveWithoutEndAndFee() {
        Rental rental = Rental.start("r1", "b1", "u2", START);

        assertThat(rental.isActive()).isTrue();
        assertThat(rental.endedAt()).isNull();
        assertThat(rental.fee()).isNull();
        assertThat(rental.startedAt()).isEqualTo(START);
    }

    @Test
    void finishingFillsEndAndFeeAndKeepsTheRest() {
        Rental finished = Rental.start("r1", "b1", "u2", START).finish(START.plusSeconds(600), 450);

        assertThat(finished.isActive()).isFalse();
        assertThat(finished.endedAt()).isEqualTo(START.plusSeconds(600));
        assertThat(finished.fee()).isEqualTo(450L);
        assertThat(finished.id()).isEqualTo("r1");
        assertThat(finished.bikeId()).isEqualTo("b1");
        assertThat(finished.userId()).isEqualTo("u2");
        assertThat(finished.startedAt()).isEqualTo(START);
    }

    @Test
    void finishingIsNotMutatingTheOriginal() {
        Rental active = Rental.start("r1", "b1", "u2", START);

        active.finish(START.plusSeconds(60), 100);

        assertThat(active.isActive()).isTrue();
    }

    @Test
    void aFinishedRentalCannotBeFinishedAgain() {
        Rental finished = Rental.start("r1", "b1", "u2", START).finish(START.plusSeconds(60), 100);

        assertThatThrownBy(() -> finished.finish(START.plusSeconds(120), 200))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("RENTAL_ALREADY_FINISHED"));
    }

    @Test
    void requiredFieldsCannotBeNull() {
        assertThatThrownBy(() -> Rental.start(null, "b1", "u2", START)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Rental.start("r1", null, "u2", START)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Rental.start("r1", "b1", null, START)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Rental.start("r1", "b1", "u2", null)).isInstanceOf(NullPointerException.class);
    }
}
