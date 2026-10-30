package hu.kerekpar.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    private List<String> run(MockHttpServletRequest request, int status) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                ((MockHttpServletResponse) res).setStatus(status);
            }
        });
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsMethodPathAnonymousUserAndStatus() throws Exception {
        List<String> logs = run(new MockHttpServletRequest("GET", "/api/bikes"), 401);

        assertThat(logs).singleElement().satisfies(line -> assertThat(line)
                .contains("GET").contains("/api/bikes").contains("user=-").contains("-> 401").contains("ms"));
    }

    @Test
    void logsTheAuthenticatedUserId() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u2", "n/a", AuthorityUtils.createAuthorityList("ROLE_RENTER")));

        List<String> logs = run(new MockHttpServletRequest("POST", "/api/rentals"), 201);

        assertThat(logs).singleElement().satisfies(line -> assertThat(line).contains("user=u2").contains("-> 201"));
    }

    @Test
    void anonymousAuthenticationIsLoggedAsNoUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication
                .AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        List<String> logs = run(new MockHttpServletRequest("POST", "/api/auth/login"), 200);

        assertThat(logs).singleElement().satisfies(line -> assertThat(line).contains("user=-").doesNotContain("anonymousUser"));
    }

    @Test
    void doesNotLogCredentialsOrTokens() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer titkos.jwt.token");
        request.setContent("{\"password\":\"Titkos-jelszo\"}".getBytes());

        List<String> logs = run(request, 200);

        assertThat(String.join("\n", logs)).doesNotContain("titkos.jwt.token").doesNotContain("Titkos-jelszo");
    }

    @Test
    void onlyApiRequestsAreFiltered() throws Exception {
        List<String> logs = run(new MockHttpServletRequest("GET", "/index.html"), 200);

        assertThat(logs).isEmpty();
    }

    @Test
    void stillLogsWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bikes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                    throw new IllegalStateException("hiba");
                }
            });
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(IllegalStateException.class);
        }

        assertThat(appender.list).hasSize(1);
    }
}
