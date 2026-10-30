package hu.kerekpar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainRulesTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private final Tariff tariff = new Tariff(300, 15);

    @Test
    void feeIsBaseFeePlusStartedMinutes() {
        assertThat(tariff.feeFor(START, START.plusSeconds(40 * 60))).isEqualTo(300 + 40 * 15);
        assertThat(tariff.feeFor(START, START.plusSeconds(40 * 60 + 1))).isEqualTo(300 + 41 * 15);
    }

    @Test
    void feeChargesAtLeastOneMinute() {
        assertThat(tariff.feeFor(START, START)).isEqualTo(300 + 15);
        assertThat(tariff.feeFor(START, START.minusSeconds(30))).isEqualTo(300 + 15);
    }

    @Test
    void rentedBikeCannotBeRentedAgain() {
        Bike rented = new Bike("b1", "City-01", BikeType.CITY, BikeStatus.AVAILABLE).rent();

        assertThat(rented.status()).isEqualTo(BikeStatus.IN_USE);
        assertThatThrownBy(rented::rent).isInstanceOf(ConflictException.class);
    }

    @Test
    void bikeInMaintenanceCannotBeRented() {
        Bike bike = new Bike("b1", "City-01", BikeType.CITY, BikeStatus.MAINTENANCE);

        assertThatThrownBy(bike::rent).isInstanceOf(ConflictException.class);
    }

    @Test
    void statusOfBikeInUseCannotBeChangedAndInUseCannotBeSetManually() {
        Bike inUse = new Bike("b1", "City-01", BikeType.CITY, BikeStatus.IN_USE);
        Bike available = new Bike("b2", "City-02", BikeType.CITY, BikeStatus.AVAILABLE);

        assertThatThrownBy(() -> inUse.changeStatus(BikeStatus.MAINTENANCE)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> available.changeStatus(BikeStatus.IN_USE)).isInstanceOf(ConflictException.class);
        assertThat(available.changeStatus(BikeStatus.MAINTENANCE).status()).isEqualTo(BikeStatus.MAINTENANCE);
    }

    @Test
    void registerValidatesNameAndType() {
        assertThatThrownBy(() -> Bike.register("b9", " a ", BikeType.CITY)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> Bike.register("b9", "City-09", null)).isInstanceOf(InvalidInputException.class);
        assertThat(Bike.register("b9", "  City-09 ", BikeType.CITY).name()).isEqualTo("City-09");
    }
}
