package hu.kerekpar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeSearchCriteria;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BikeSearchCriteriaTest {

    private static final Bike CITY = new Bike("b1", "City-01", BikeType.CITY, BikeStatus.AVAILABLE);
    private static final Bike HEGYI = new Bike("b2", "Hegyi-Árpád", BikeType.MOUNTAIN, BikeStatus.IN_USE);
    private static final Bike EBIKE = new Bike("b3", "E-bike_50%", BikeType.ELECTRIC, BikeStatus.MAINTENANCE);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t\n"})
    void blankTextMeansNoNameFilter(String text) {
        BikeSearchCriteria criteria = BikeSearchCriteria.of(text, null, null, null);

        assertThat(criteria.nameContains()).isNull();
        assertThat(criteria.matches(CITY)).isTrue();
    }

    @Test
    void textIsTrimmed() {
        assertThat(BikeSearchCriteria.of("  city ", null, null, null).nameContains()).isEqualTo("city");
    }

    @Test
    void defaultLimitIsAppliedWhenMissing() {
        assertThat(BikeSearchCriteria.of(null, null, null, null).limit()).isEqualTo(BikeSearchCriteria.DEFAULT_LIMIT);
    }

    @Test
    void limitBoundariesAreInclusive() {
        assertThat(BikeSearchCriteria.of(null, null, null, 1).limit()).isEqualTo(1);
        assertThat(BikeSearchCriteria.of(null, null, null, BikeSearchCriteria.MAX_LIMIT).limit()).isEqualTo(100);
    }

    @Test
    void outOfRangeLimitIsRejected() {
        for (int bad : new int[] {0, -1, 101, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertThatThrownBy(() -> BikeSearchCriteria.of(null, null, null, bad))
                    .isInstanceOfSatisfying(InvalidInputException.class,
                            e -> assertThat(e.code()).isEqualTo("INVALID_LIMIT"));
        }
    }

    @Test
    void textLengthIsCountedInCodePointsNotUtf16Units() {
        String fifty = "😀".repeat(50);

        assertThat(BikeSearchCriteria.of(fifty, null, null, null).nameContains()).isEqualTo(fifty);
        assertThat(BikeSearchCriteria.of("x".repeat(50), null, null, null).nameContains()).hasSize(50);
        assertThatThrownBy(() -> BikeSearchCriteria.of("x".repeat(51), null, null, null))
                .isInstanceOfSatisfying(InvalidInputException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_SEARCH_TEXT"));
        assertThatThrownBy(() -> BikeSearchCriteria.of("😀".repeat(51), null, null, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void canonicalConstructorEnforcesTheSameInvariants() {
        assertThatThrownBy(() -> new BikeSearchCriteria("", null, null, 10)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new BikeSearchCriteria("a", null, null, 0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new BikeSearchCriteria("x".repeat(51), null, null, 10))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void nameMatchIsCaseInsensitiveSubstring() {
        assertThat(BikeSearchCriteria.of("CITY", null, null, null).matches(CITY)).isTrue();
        assertThat(BikeSearchCriteria.of("y-0", null, null, null).matches(CITY)).isTrue();
        assertThat(BikeSearchCriteria.of("city-01", null, null, null).matches(CITY)).isTrue();
        assertThat(BikeSearchCriteria.of("city-02", null, null, null).matches(CITY)).isFalse();
        assertThat(BikeSearchCriteria.of("hegyi", null, null, null).matches(CITY)).isFalse();
    }

    @Test
    void accentedLettersAreMatchedCaseInsensitively() {
        assertThat(BikeSearchCriteria.of("árpád", null, null, null).matches(HEGYI)).isTrue();
        assertThat(BikeSearchCriteria.of("ÁRPÁD", null, null, null).matches(HEGYI)).isTrue();
        assertThat(BikeSearchCriteria.of("arpad", null, null, null).matches(HEGYI))
                .as("az ékezet nem hagyható el").isFalse();
    }

    @Test
    void specialCharactersAreLiteralNotPatterns() {
        assertThat(BikeSearchCriteria.of("%", null, null, null).matches(EBIKE)).isTrue();
        assertThat(BikeSearchCriteria.of("%", null, null, null).matches(CITY)).isFalse();
        assertThat(BikeSearchCriteria.of("_", null, null, null).matches(EBIKE)).isTrue();
        assertThat(BikeSearchCriteria.of("_", null, null, null).matches(CITY)).isFalse();
        assertThat(BikeSearchCriteria.of(".*", null, null, null).matches(CITY)).isFalse();
        assertThat(BikeSearchCriteria.of("c%y", null, null, null).matches(CITY)).isFalse();
        assertThat(BikeSearchCriteria.of("[a-z]", null, null, null).matches(CITY)).isFalse();
        assertThat(BikeSearchCriteria.of("' OR '1'='1", null, null, null).matches(CITY)).isFalse();
    }

    @Test
    void typeAndStatusFiltersAreExact() {
        assertThat(BikeSearchCriteria.of(null, BikeType.CITY, null, null).matches(CITY)).isTrue();
        assertThat(BikeSearchCriteria.of(null, BikeType.CITY, null, null).matches(HEGYI)).isFalse();
        assertThat(BikeSearchCriteria.of(null, null, BikeStatus.IN_USE, null).matches(HEGYI)).isTrue();
        assertThat(BikeSearchCriteria.of(null, null, BikeStatus.IN_USE, null).matches(CITY)).isFalse();
    }

    @Test
    void filtersCombineWithAnd() {
        BikeSearchCriteria criteria = BikeSearchCriteria.of("hegyi", BikeType.MOUNTAIN, BikeStatus.IN_USE, null);

        assertThat(criteria.matches(HEGYI)).isTrue();
        assertThat(BikeSearchCriteria.of("hegyi", BikeType.CITY, BikeStatus.IN_USE, null).matches(HEGYI)).isFalse();
        assertThat(BikeSearchCriteria.of("hegyi", BikeType.MOUNTAIN, BikeStatus.AVAILABLE, null).matches(HEGYI)).isFalse();
        assertThat(BikeSearchCriteria.of("city", BikeType.MOUNTAIN, BikeStatus.IN_USE, null).matches(HEGYI)).isFalse();
    }

    @Test
    void noFilterMatchesEveryBike() {
        BikeSearchCriteria all = BikeSearchCriteria.of(null, null, null, null);

        assertThat(all.matches(CITY) && all.matches(HEGYI) && all.matches(EBIKE)).isTrue();
    }
}
