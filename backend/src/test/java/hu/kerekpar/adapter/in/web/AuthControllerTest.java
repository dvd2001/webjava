package hu.kerekpar.adapter.in.web;

import static hu.kerekpar.support.TestAuth.DEMO_PASSWORD;
import static hu.kerekpar.support.TestAuth.loginJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hu.kerekpar.adapter.in.web.security.LoginAttemptLimiter;
import hu.kerekpar.support.TestTokens;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A bejelentkezés. A tesztek külön felhasználónevet használnak a zárolás miatt, és a context osztályonként új,
 * hogy a próbálkozás-számláló ne szivárogjon más osztályok tesztjeibe.
 */
@SpringBootTest(properties = "kerekpar.security.jwt-secret=" + TestTokens.SECRET)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.Random.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void resetLoginThrottle() {
        for (String username : new String[] {"anna", "peter", "eszter", "gabor", "lockout-teszt", "nincs-ilyen", ""}) {
            limiter.reset(username);
        }
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, password))).andReturn();
    }

    @Test
    void successfulLoginReturnsABearerTokenForTheUser() throws Exception {
        long before = Instant.now().toEpochMilli();

        MvcResult result = login("peter", DEMO_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.tokenType")).isEqualTo("Bearer");
        assertThat(JsonPath.<String>read(body, "$.user.id")).isEqualTo("u2");
        assertThat(JsonPath.<String>read(body, "$.user.name")).isEqualTo("Nagy Péter");
        assertThat(JsonPath.<String>read(body, "$.user.role")).isEqualTo("renter");
        assertThat(body).doesNotContain("password").doesNotContain("passwordHash").doesNotContain(DEMO_PASSWORD);

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo("u2");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("RENTER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(TestTokens.ISSUER);
        long expiresAt = ((Number) JsonPath.read(body, "$.expiresAt")).longValue();
        assertThat(expiresAt).isEqualTo(jwt.getExpiresAt().toEpochMilli());
        assertThat(expiresAt).isBetween(before + 29 * 60_000L, before + 31 * 60_000L);
    }

    @Test
    void managerGetsTheManagerRoleInTheToken() throws Exception {
        String body = login("anna", DEMO_PASSWORD).getResponse().getContentAsString();

        assertThat(jwtDecoder.decode(JsonPath.<String>read(body, "$.accessToken")).<String>getClaim("role"))
                .isEqualTo("MANAGER");
    }

    @Test
    void usernameIsCaseInsensitiveAndTrimmed() throws Exception {
        assertThat(login("  GABOR ", DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownUserAreIndistinguishable() throws Exception {
        MvcResult wrongPassword = login("peter", "rossz-jelszo");
        MvcResult unknownUser = login("nincs-ilyen", "rossz-jelszo");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
        assertThat(JsonPath.<String>read(wrongPassword.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void passwordIsCaseSensitive() throws Exception {
        assertThat(login("eszter", DEMO_PASSWORD.toUpperCase()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void missingBlankAndOversizedCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(login("", "").getResponse().getStatus()).isEqualTo(401);
        assertThat(login("peter", "x".repeat(200)).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("a".repeat(200), DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void usernameIsLockedAfterFiveFailuresEvenForTheCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("lockout-teszt", "rossz").getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult locked = login("lockout-teszt", DEMO_PASSWORD);

        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(JsonPath.<String>read(locked.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
    }

    @Test
    void lockoutAppliesToTheRealUserAndDoesNotAffectOthers() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("anna", "rossz");
        }

        assertThat(login("anna", DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(429);
        assertThat(login("ANNA", DEMO_PASSWORD).getResponse().getStatus()).as("kisbetű/nagybetű kerülő út").isEqualTo(429);
        assertThat(login("gabor", DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                assertThat(login("peter", "rossz").getResponse().getStatus()).isEqualTo(401);
            }
            assertThat(login("peter", DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }
    }
}
