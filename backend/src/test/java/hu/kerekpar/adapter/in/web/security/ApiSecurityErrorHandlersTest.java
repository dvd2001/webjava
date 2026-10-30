package hu.kerekpar.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class ApiSecurityErrorHandlersTest {

    private final ApiSecurityErrorHandlers handlers = new ApiSecurityErrorHandlers(new ObjectMapper());
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bikes");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void authenticationFailureIs401WithBearerChallengeAndJsonBody() throws Exception {
        handlers.commence(request, response, new InvalidBearerTokenException("lejárt"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void theErrorBodyDoesNotRevealWhyTheTokenWasRejected() throws Exception {
        handlers.commence(request, response, new InvalidBearerTokenException("signature mismatch, kid=secret"));

        assertThat(response.getContentAsString()).doesNotContain("signature").doesNotContain("kid").doesNotContain("secret");
    }

    @Test
    void anyAuthenticationExceptionGetsTheSameAnswer() throws Exception {
        MockHttpServletResponse other = new MockHttpServletResponse();

        handlers.commence(request, response, new InvalidBearerTokenException("x"));
        handlers.commence(request, other, new BadCredentialsException("y"));

        assertThat(other.getContentAsString()).isEqualTo(response.getContentAsString());
    }

    @Test
    void accessDeniedIs403WithJsonBody() throws Exception {
        handlers.handle(request, response, new AccessDeniedException("nem vezető"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"").doesNotContain("nem vezető");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void hungarianMessagesKeepTheirAccents() throws Exception {
        handlers.handle(request, response, new AccessDeniedException("x"));

        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("jogosultságod");
    }
}
