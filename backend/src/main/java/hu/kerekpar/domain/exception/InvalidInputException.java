package hu.kerekpar.domain.exception;

public class InvalidInputException extends DomainException {

    public InvalidInputException(String code, String message) {
        super(code, message);
    }
}
