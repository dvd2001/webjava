package hu.kerekpar.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MapperTest {

    @Test
    void entityIdsRoundTripAndRejectMalformedValues() {
        assertThat(EntityIds.toDomain(EntityIds.BIKE, 7)).isEqualTo("b7");
        assertThat(EntityIds.parse(EntityIds.BIKE, "b7")).contains(7L);
        assertThat(EntityIds.parse(EntityIds.BIKE, "r7")).isEmpty();
        assertThat(EntityIds.parse(EntityIds.BIKE, "b")).isEmpty();
        assertThat(EntityIds.parse(EntityIds.BIKE, "b0")).isEmpty();
        assertThat(EntityIds.parse(EntityIds.BIKE, "b-3")).isEmpty();
        assertThat(EntityIds.parse(EntityIds.BIKE, "b99999999999999999999")).isEmpty();
        assertThatThrownBy(() -> EntityIds.require(EntityIds.BIKE, "nincs")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bikeMapperIsLossless() {
        Bike bike = new Bike("b5", "E-bike-01", BikeType.ELECTRIC, BikeStatus.MAINTENANCE);

        BikeJpaEntity entity = BikeMapper.toNewEntity(bike);

        assertThat(entity.getId()).isEqualTo(5L);
        assertThat(BikeMapper.toDomain(entity)).isEqualTo(bike);

        BikeMapper.applyTo(bike.changeStatus(BikeStatus.AVAILABLE), entity);
        assertThat(entity.getId()).isEqualTo(5L);
        assertThat(BikeMapper.toDomain(entity).status()).isEqualTo(BikeStatus.AVAILABLE);
    }

    @Test
    void rentalMapperKeepsActiveAndFinishedState() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Rental active = Rental.start("r2", "b3", "u4", start);

        RentalJpaEntity entity = RentalMapper.toNewEntity(active);
        assertThat(RentalMapper.toDomain(entity)).isEqualTo(active);

        Rental finished = active.finish(start.plusSeconds(90), 345);
        RentalMapper.applyTo(finished, entity);
        assertThat(RentalMapper.toDomain(entity)).isEqualTo(finished);
        assertThat(RentalMapper.toDomain(entity).fee()).isEqualTo(345L);
    }
}
