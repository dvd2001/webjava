package hu.kerekpar.domain.model;

import java.time.Duration;
import java.time.Instant;

/** Díjszabás: kezdődíj + megkezdett percek × percdíj (egész forintban). */
public record Tariff(long baseFee, long perMinute) {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    public Tariff {
        if (baseFee < 0 || perMinute < 0) {
            throw new IllegalArgumentException("A díjak nem lehetnek negatívak.");
        }
    }

    /** Megkezdett percek száma, legalább 1. */
    public static long billableMinutes(Instant start, Instant end) {
        long elapsedMillis = Math.max(0, Duration.between(start, end).toMillis());
        return Math.max(1, (elapsedMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE);
    }

    public long feeFor(Instant start, Instant end) {
        return baseFee + billableMinutes(start, end) * perMinute;
    }
}
