package hu.kerekpar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BikeTest {

    private static Bike bike(BikeStatus status) {
        return new Bike("b1", "City-01", BikeType.CITY, status);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "a", " a ", "\t\n"})
    void tooShortOrBlankNamesAreRejected(String name) {
        assertThatThrownBy(() -> Bike.normalizeName(name))
                .isInstanceOfSatisfying(InvalidInputException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_BIKE_NAME"));
    }

    @Test
    void nameLengthBoundariesAreInclusive() {
        assertThat(Bike.normalizeName("ab")).isEqualTo("ab");
        assertThat(Bike.normalizeName("x".repeat(50))).hasSize(50);
        assertThatThrownBy(() -> Bike.normalizeName("x".repeat(51))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void nameIsTrimmedBeforeTheLengthCheck() {
        assertThat(Bike.normalizeName("  City-03 \t")).isEqualTo("City-03");
        assertThat(Bike.normalizeName(" " + "x".repeat(50) + " ")).hasSize(50);
    }

    @Test
    void registeredBikeIsAvailableAndKeepsItsData() {
        Bike registered = Bike.register("b9", " Hegyi-09 ", BikeType.MOUNTAIN);

        assertThat(registered).isEqualTo(new Bike("b9", "Hegyi-09", BikeType.MOUNTAIN, BikeStatus.AVAILABLE));
    }

    @Test
    void registeringWithoutTypeIsRejected() {
        assertThatThrownBy(() -> Bike.register("b9", "Hegyi-09", null))
                .isInstanceOfSatisfying(InvalidInputException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_BIKE_TYPE"));
    }

    @Test
    void availableBikeCanBeRentedAndGivenBack() {
        Bike rented = bike(BikeStatus.AVAILABLE).rent();
        Bike returned = rented.giveBack();

        assertThat(rented.status()).isEqualTo(BikeStatus.IN_USE);
        assertThat(returned.status()).isEqualTo(BikeStatus.AVAILABLE);
        assertThat(returned.id()).isEqualTo("b1");
    }

    @ParameterizedTest
    @EnumSource(value = BikeStatus.class, names = {"IN_USE", "MAINTENANCE"})
    void onlyAvailableBikesCanBeRented(BikeStatus status) {
        assertThatThrownBy(() -> bike(status).rent())
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("BIKE_NOT_AVAILABLE"));
    }

    @ParameterizedTest
    @EnumSource(value = BikeStatus.class, names = {"AVAILABLE", "MAINTENANCE"})
    void onlyBikesInUseCanBeGivenBack(BikeStatus status) {
        assertThatThrownBy(() -> bike(status).giveBack())
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("BIKE_NOT_IN_USE"));
    }

    @ParameterizedTest
    @EnumSource(value = BikeStatus.class, names = {"AVAILABLE", "MAINTENANCE"})
    void statusCanBeSwitchedBetweenAvailableAndMaintenance(BikeStatus from) {
        BikeStatus target = from == BikeStatus.AVAILABLE ? BikeStatus.MAINTENANCE : BikeStatus.AVAILABLE;

        assertThat(bike(from).changeStatus(target).status()).isEqualTo(target);
        assertThat(bike(from).changeStatus(from).status()).isEqualTo(from);
    }

    @Test
    void invalidStatusChangesAreRejected() {
        assertThatThrownBy(() -> bike(BikeStatus.AVAILABLE).changeStatus(null))
                .isInstanceOfSatisfying(InvalidInputException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_STATUS"));
        assertThatThrownBy(() -> bike(BikeStatus.AVAILABLE).changeStatus(BikeStatus.IN_USE))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("STATUS_NOT_SETTABLE"));
        assertThatThrownBy(() -> bike(BikeStatus.IN_USE).changeStatus(BikeStatus.AVAILABLE))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("BIKE_IN_USE"));
    }
}
