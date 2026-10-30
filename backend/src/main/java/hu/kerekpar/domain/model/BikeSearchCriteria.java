package hu.kerekpar.domain.model;

import hu.kerekpar.domain.exception.InvalidInputException;
import java.util.Locale;

/**
 * Bicikli-keresés feltételei (ÉS kapcsolat, mind elhagyható). A név kis- és nagybetűtől független részlet-egyezés,
 * a speciális karakterek szó szerint értendők (nincs mintanyelv), így a keresés nem függ az adatbázis szabályaitól.
 */
public record BikeSearchCriteria(String nameContains, BikeType type, BikeStatus status, int limit) {

    public static final int MAX_TEXT_LENGTH = 50;
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public BikeSearchCriteria {
        if (nameContains != null && (nameContains.isEmpty() || nameContains.codePointCount(0, nameContains.length()) > MAX_TEXT_LENGTH)) {
            throw invalidText();
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalidLimit();
        }
    }

    /** A kérésből érkező nyers értékek normalizálása és ellenőrzése; az üres szöveg és a hiányzó limit alapértelmezést kap. */
    public static BikeSearchCriteria of(String text, BikeType type, BikeStatus status, Integer limit) {
        String normalized = text == null ? null : text.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && normalized.codePointCount(0, normalized.length()) > MAX_TEXT_LENGTH) {
            throw invalidText();
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw invalidLimit();
        }
        return new BikeSearchCriteria(normalized, type, status, effectiveLimit);
    }

    public boolean matches(Bike bike) {
        if (type != null && bike.type() != type) {
            return false;
        }
        if (status != null && bike.status() != status) {
            return false;
        }
        return nameContains == null
                || bike.name().toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT));
    }

    private static InvalidInputException invalidText() {
        return new InvalidInputException("INVALID_SEARCH_TEXT",
                "A keresett szöveg legfeljebb " + MAX_TEXT_LENGTH + " karakter lehet.");
    }

    private static InvalidInputException invalidLimit() {
        return new InvalidInputException("INVALID_LIMIT", "A limit 1 és " + MAX_LIMIT + " között lehet.");
    }
}
