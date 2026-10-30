package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.api.model.ApiErrorDto;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.exception.DomainException;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.exception.TooManyRequestsException;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Minden hibát a szerződés {@link ApiErrorDto} törzsére képez le. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorDto> handleDomain(DomainException e) {
        HttpStatus status = statusOf(e);
        log.warn("Üzleti hiba: {} {} - {}", status.value(), e.code(), e.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(new ApiErrorDto(e.code(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpected(Exception e) {
        log.error("Váratlan hiba", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorDto("INTERNAL_ERROR", "Váratlan hiba történt."));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Érvénytelen kérés törzs: {}", ex.getMessage());
        return ResponseEntity.status(status)
                .body(new ApiErrorDto("INVALID_REQUEST", "Érvénytelen kérés: hibás vagy hiányzó adat."));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String parameter = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : String.valueOf(ex.getPropertyName());
        log.warn("Érvénytelen paraméter típus: {}", parameter);
        return ResponseEntity.status(status)
                .body(new ApiErrorDto("INVALID_REQUEST", "Érvénytelen paraméter: " + parameter));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex instanceof MissingRequestHeaderException missing
                ? "Hiányzó fejléc: " + missing.getHeaderName()
                : "Érvénytelen kérés.";
        log.warn("Kérés kötési hiba: {}", message);
        return ResponseEntity.status(status).body(new ApiErrorDto("INVALID_REQUEST", message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        log.warn("Kérés elutasítva: {} {}", statusCode.value(), ex.getClass().getSimpleName());
        return ResponseEntity.status(statusCode).headers(headers)
                .body(new ApiErrorDto("REQUEST_ERROR", "A kérés nem teljesíthető (" + statusCode.value() + ")."));
    }

    private static HttpStatus statusOf(DomainException e) {
        if (e instanceof UnauthenticatedException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (e instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (e instanceof ForbiddenException) {
            return HttpStatus.FORBIDDEN;
        }
        if (e instanceof ConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (e instanceof TooManyRequestsException) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (e instanceof InvalidInputException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
