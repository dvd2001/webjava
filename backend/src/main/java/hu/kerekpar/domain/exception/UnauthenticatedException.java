package hu.kerekpar.domain.exception;

/** A hitelesítés hiányzik vagy sikertelen (HTTP 401-re képződik). */
public class UnauthenticatedException extends DomainException {

    public UnauthenticatedException(String code, String message) {
        super(code, message);
    }
}
