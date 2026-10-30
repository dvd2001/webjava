package hu.kerekpar.adapter.out.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** A megsértett adatbázis-constraint neve, ha a hibalánc tartalmaz ilyet; különben null. */
    static String nameOf(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    static boolean isViolationOf(DataIntegrityViolationException exception, String constraint) {
        return constraint.equals(nameOf(exception));
    }
}
