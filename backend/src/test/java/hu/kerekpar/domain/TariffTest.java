package hu.kerekpar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.model.Tariff;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TariffTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void negativeFeesAreRejected() {
        assertThatThrownBy(() -> new Tariff(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tariff(10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Tariff(0, 0).feeFor(START, START.plusSeconds(3600))).isZero();
    }

    @Test
    void billableMinutesRoundUpAndStartAtOne() {
        assertThat(Tariff.billableMinutes(START, START)).isEqualTo(1);
        assertThat(Tariff.billableMinutes(START, START.plusMillis(1))).isEqualTo(1);
        assertThat(Tariff.billableMinutes(START, START.plusSeconds(60))).isEqualTo(1);
        assertThat(Tariff.billableMinutes(START, START.plusSeconds(60).plusMillis(1))).isEqualTo(2);
        assertThat(Tariff.billableMinutes(START, START.plusSeconds(3600))).isEqualTo(60);
    }

    @Test
    void endBeforeStartIsChargedAsOneMinute() {
        assertThat(Tariff.billableMinutes(START, START.minusSeconds(600))).isEqualTo(1);
    }

    @Test
    void feeIsBaseFeePlusMinutesTimesRate() {
        Tariff electric = new Tariff(500, 35);

        assertThat(electric.feeFor(START, START.plusSeconds(10 * 60 + 5))).isEqualTo(500 + 11 * 35);
        assertThat(electric.baseFee()).isEqualTo(500);
        assertThat(electric.perMinute()).isEqualTo(35);
    }
}
