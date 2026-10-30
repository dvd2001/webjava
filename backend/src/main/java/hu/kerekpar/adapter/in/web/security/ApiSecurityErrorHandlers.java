package hu.kerekpar.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.kerekpar.adapter.in.web.api.model.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** A biztonsági szűrők hibáit (401, 403) a szerződés {@link ApiErrorDto} törzsével adja vissza. */
public class ApiSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityErrorHandlers.class);

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Hiányzó, hibás, lejárt vagy hamisított token. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        log.warn("401 {} {} ({})", request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED,
                new ApiErrorDto("UNAUTHENTICATED", "Hiányzó, érvénytelen vagy lejárt hitelesítés."));
    }

    /** Hitelesített, de a végponthoz nem megfelelő szerepkörű felhasználó. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.warn("403 {} {} user={}", request.getMethod(), request.getRequestURI(),
                request.getUserPrincipal() == null ? "-" : request.getUserPrincipal().getName());
        write(response, HttpStatus.FORBIDDEN,
                new ApiErrorDto("FORBIDDEN", "Ehhez a művelethez nincs jogosultságod."));
    }

    private void write(HttpServletResponse response, HttpStatus status, ApiErrorDto body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
