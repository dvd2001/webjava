package hu.kerekpar.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;

/** Az azonosítókat a tábla identity-sorozatából kéri el (a domain mentés előtt kér azonosítót). */
class IdSequence {

    static final String BIKE_TABLE = "bike";
    static final String RENTAL_TABLE = "rental";

    private final EntityManager entityManager;

    IdSequence(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** A {@code table} csak a fenti állandók egyike lehet. */
    long next(String table) {
        return entityManager.unwrap(Session.class)
                .createNativeQuery("select nextval(pg_get_serial_sequence(:table, 'id'))", Long.class)
                .setParameter("table", table)
                .getSingleResult();
    }
}
