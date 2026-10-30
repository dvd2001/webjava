package hu.kerekpar.domain.exception;

/** Túl sok sikertelen próbálkozás miatt a művelet átmenetileg nem engedélyezett (HTTP 429-re képződik). */
public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}
