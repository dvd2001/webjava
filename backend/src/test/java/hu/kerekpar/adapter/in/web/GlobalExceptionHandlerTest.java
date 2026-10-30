package hu.kerekpar.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import hu.kerekpar.adapter.in.web.api.model.ApiErrorDto;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.exception.DomainException;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.exception.TooManyRequestsException;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static final class OtherDomainException extends DomainException {
        OtherDomainException() {
            super("OTHER", "egyéb");
        }
    }

    private void assertMapping(DomainException exception, HttpStatus expected) {
        ResponseEntity<ApiErrorDto> response = handler.handleDomain(exception);

        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody().getCode()).isEqualTo(exception.code());
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
    }

    @Test
    void domainExceptionsMapToTheirHttpStatus() {
        assertMapping(new InvalidInputException("A", "m"), HttpStatus.BAD_REQUEST);
        assertMapping(new UnauthenticatedException("B", "m"), HttpStatus.UNAUTHORIZED);
        assertMapping(new ForbiddenException("C", "m"), HttpStatus.FORBIDDEN);
        assertMapping(new NotFoundException("D", "m"), HttpStatus.NOT_FOUND);
        assertMapping(new ConflictException("E", "m"), HttpStatus.CONFLICT);
        assertMapping(new TooManyRequestsException("F", "m"), HttpStatus.TOO_MANY_REQUESTS);
        assertMapping(new OtherDomainException(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void onlyUnauthenticatedAnswersCarryTheBearerChallenge() {
        assertThat(handler.handleDomain(new UnauthenticatedException("B", "m")).getHeaders()
                .getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(handler.handleDomain(new ForbiddenException("C", "m")).getHeaders()
                .containsKey(HttpHeaders.WWW_AUTHENTICATE)).isFalse();
    }

    @Test
    void unexpectedErrorsAreGenericAndDoNotLeakTheCause() {
        ResponseEntity<ApiErrorDto> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://db password=titok at hu.kerekpar.Secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("jdbc").doesNotContain("titok").doesNotContain("Secret");
    }
}
