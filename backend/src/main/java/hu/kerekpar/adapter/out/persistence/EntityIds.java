package hu.kerekpar.adapter.out.persistence;

import java.util.Optional;

/** A domain szöveges azonosítói ("b3") és az adatbázis bigint kulcsai közötti leképezés. */
final class EntityIds {

    static final String BIKE = "b";
    static final String RENTAL = "r";
    static final String USER = "u";

    private EntityIds() {
    }

    static String toDomain(String prefix, long id) {
        return prefix + id;
    }

    /** Üres, ha az azonosító nem a megadott előtaggal és pozitív számmal áll elő. */
    static Optional<Long> parse(String prefix, String domainId) {
        if (domainId == null || !domainId.startsWith(prefix) || domainId.length() == prefix.length()) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(domainId.substring(prefix.length()));
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static long require(String prefix, String domainId) {
        return parse(prefix, domainId).orElseThrow(
                () -> new IllegalArgumentException("Érvénytelen azonosító: " + domainId));
    }
}
