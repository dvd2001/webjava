package hu.kerekpar.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;

import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.Rental;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoDataSeederTest {

    private static final Instant NOW = Instant.parse("2026-03-14T12:00:00Z");

    private final InMemoryBikeRepository bikes = new InMemoryBikeRepository();
    private final InMemoryRentalRepository rentals = new InMemoryRentalRepository();

    private void seed() {
        new DemoDataSeeder(bikes, rentals, new InMemoryTariffRepository(), () -> NOW).seed();
    }

    @Test
    void seedsSixBikesAndThreeRentals() {
        seed();

        assertThat(bikes.findAll()).extracting(Bike::name)
                .containsExactly("City-01", "City-02", "Hegyi-01", "Hegyi-02", "E-bike-01", "E-bike-02");
        assertThat(rentals.findAll()).extracting(Rental::id).containsExactlyInAnyOrder("r0", "r1", "r2");
    }

    @Test
    void everyBikeInUseHasExactlyOneActiveRentalAndNoOtherBikeHasOne() {
        seed();

        List<String> activeBikeIds = rentals.findAll().stream().filter(Rental::isActive).map(Rental::bikeId).toList();
        List<String> inUseBikeIds = bikes.findAll().stream()
                .filter(b -> b.status() == BikeStatus.IN_USE).map(Bike::id).toList();

        assertThat(activeBikeIds).containsExactlyInAnyOrderElementsOf(inUseBikeIds);
    }

    @Test
    void finishedRentalHasAFeeFromTheCityTariffAndRelativeTimes() {
        seed();

        Rental finished = rentals.findById("r0").orElseThrow();
        assertThat(finished.isActive()).isFalse();
        assertThat(finished.fee()).isEqualTo(300 + 40 * 15);
        assertThat(Duration.between(finished.startedAt(), finished.endedAt())).isEqualTo(Duration.ofMinutes(40));
        assertThat(rentals.findById("r1").orElseThrow().startedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(25)));
        assertThat(rentals.findById("r2").orElseThrow().startedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(70)));
    }
}
