package hu.kerekpar.domain.exception;

/** Az üzleti szabályok megsértését jelző kivételek közös őse. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
