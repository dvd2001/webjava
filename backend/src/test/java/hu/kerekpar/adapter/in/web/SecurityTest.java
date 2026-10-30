package hu.kerekpar.adapter.in.web;

import static hu.kerekpar.support.TestAuth.MANAGER;
import static hu.kerekpar.support.TestAuth.OTHER_RENTER;
import static hu.kerekpar.support.TestAuth.RENTER;
import static hu.kerekpar.support.TestAuth.bearer;
import static hu.kerekpar.support.TestAuth.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.kerekpar.support.TestAuth;
import hu.kerekpar.support.TestTokens;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A SecurityFilterChain viselkedése: ki, mit érhet el (401 / 403 / sikeres), és mit nem fogad el a szerver.
 * Az üzleti szabályok (pl. tulajdonjog) használati eset szintű ellenőrzése is itt van a végpontokon át bizonyítva.
 */
@SpringBootTest(properties = "kerekpar.security.jwt-secret=" + TestTokens.SECRET)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.Random.class)
class SecurityTest {

    private record Endpoint(HttpMethod method, String path, String body, boolean managerAllowed, boolean renterAllowed) {
    }

    private static List<Endpoint> endpoints() {
        String uniqueBike = "{\"name\":\"Sec-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"type\":\"city\"}";
        return List.of(
                new Endpoint(HttpMethod.GET, "/api/users", null, true, false),
                new Endpoint(HttpMethod.GET, "/api/tariffs", null, true, true),
                new Endpoint(HttpMethod.GET, "/api/bikes", null, true, true),
                new Endpoint(HttpMethod.GET, "/api/bikes/search", null, true, true),
                new Endpoint(HttpMethod.POST, "/api/bikes", uniqueBike, true, false),
                new Endpoint(HttpMethod.PATCH, "/api/bikes/b1/status", "{\"status\":\"maintenance\"}", true, false),
                new Endpoint(HttpMethod.GET, "/api/rentals", null, true, true),
                new Endpoint(HttpMethod.POST, "/api/rentals", "{\"bikeId\":\"b3\"}", false, true),
                new Endpoint(HttpMethod.POST, "/api/rentals/r1/return", null, true, true));
    }

    @Autowired
    private MockMvc mvc;

    private MockHttpServletRequestBuilder build(Endpoint endpoint, String token) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return token == null ? builder : builder.with(bearer(token));
    }

    private int statusOf(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }

    // ---------------------------------------------------------------- 401 és 403

    @Test
    void roleMatrixAnonymousGets401WrongRoleGets403AllowedRoleIsNeverRejectedBySecurity() throws Exception {
        String manager = login(mvc, MANAGER);
        String renter = login(mvc, RENTER);
        SoftAssertions softly = new SoftAssertions();
        for (Endpoint endpoint : endpoints()) {
            String label = endpoint.method() + " " + endpoint.path();
            softly.assertThat(statusOf(build(endpoint, null))).as("névtelen: " + label).isEqualTo(401);

            int managerStatus = statusOf(build(endpoint, manager));
            int renterStatus = statusOf(build(endpoint, renter));
            if (endpoint.managerAllowed()) {
                softly.assertThat(managerStatus).as("állomásvezető: " + label).isNotIn(401, 403);
            } else {
                softly.assertThat(managerStatus).as("állomásvezető: " + label).isEqualTo(403);
            }
            if (endpoint.renterAllowed()) {
                softly.assertThat(renterStatus).as("bérlő: " + label).isNotIn(401, 403);
            } else {
                softly.assertThat(renterStatus).as("bérlő: " + label).isEqualTo(403);
            }
        }
        softly.assertAll();
    }

    @Test
    void unauthenticatedResponseIsJsonWithBearerChallenge() throws Exception {
        mvc.perform(get("/api/bikes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void forbiddenResponseIsJsonWithoutLeakingDetails() throws Exception {
        String renter = login(mvc, RENTER);

        mvc.perform(get("/api/users").with(bearer(renter)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Ehhez a művelethez nincs jogosultságod."));
    }

    @Test
    void routesNotInTheAccessRulesAreDeniedInsteadOfBeingOpen() throws Exception {
        String manager = login(mvc, MANAGER);

        mvc.perform(get("/api/nincs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/nincs").with(bearer(manager))).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.DELETE, "/api/bikes/b1").with(bearer(manager)))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.PUT, "/api/bikes").with(bearer(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- tokenek elleni támadások

    @Test
    void invalidTokensAreRejectedWith401() throws Exception {
        Instant now = Instant.now();
        String valid = TestTokens.valid("u2", "RENTER");
        List<String> badTokens = List.of(
                "lejart", TestTokens.signed(TestTokens.SECRET, TestTokens.ISSUER, "u2", "RENTER",
                        now.minusSeconds(7200), now.minusSeconds(3600)),
                "rossz-kiallito", TestTokens.signed(TestTokens.SECRET, "masvalaki", "u2", "RENTER", now, now.plusSeconds(600)),
                "rossz-kulcs", TestTokens.signed("another-secret-another-secret-another-secret", TestTokens.ISSUER,
                        "u2", "RENTER", now, now.plusSeconds(600)),
                "alg-none", TestTokens.unsigned("u1", "MANAGER"),
                "atirt-szerepkor", TestTokens.withTamperedPayload(valid, "MANAGER"),
                "szemet", "ez.nem.token",
                "csonkolt", valid.substring(0, valid.length() - 5));

        SoftAssertions softly = new SoftAssertions();
        for (int i = 0; i < badTokens.size(); i += 2) {
            MvcResult result = mvc.perform(get("/api/bikes").with(bearer(badTokens.get(i + 1)))).andReturn();
            softly.assertThat(result.getResponse().getStatus()).as(badTokens.get(i)).isEqualTo(401);
            softly.assertThat(result.getResponse().getContentAsString()).as(badTokens.get(i)).contains("UNAUTHENTICATED");
        }
        softly.assertAll();
    }

    @Test
    void onlyTheBearerSchemeInTheAuthorizationHeaderIsAccepted() throws Exception {
        String token = TestTokens.valid("u2", "RENTER");

        mvc.perform(get("/api/bikes").header("Authorization", "Basic " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bikes").header("Authorization", "Bearer ")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bikes").param("access_token", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bikes").header("X-Auth-Token", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bikes").with(bearer(token))).andExpect(status().isOk());
    }

    @Test
    void tokenSignedForAnUnknownUserIsRejectedByWriteAndScopedOperations() throws Exception {
        String ghost = TestTokens.valid("u999", "RENTER");

        mvc.perform(post("/api/rentals").with(bearer(ghost)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"b3\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ACTOR"));
        mvc.perform(get("/api/rentals").with(bearer(ghost)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ACTOR"));
    }

    @Test
    void roleClaimAloneIsNotTrustedTheUseCaseChecksTheStoredRole() throws Exception {
        String renterClaimingManager = TestTokens.valid("u2", "MANAGER");

        mvc.perform(post("/api/bikes").with(bearer(renterClaimingManager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hamis-01\",\"type\":\"city\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
    }

    @Test
    void tokenWithoutRoleCanReadButNotWrite() throws Exception {
        String noRole = TestTokens.valid("u1", null);

        mvc.perform(get("/api/bikes").with(bearer(noRole))).andExpect(status().isOk());
        mvc.perform(post("/api/bikes").with(bearer(noRole)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Szerep-01\",\"type\":\"city\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------- az actor nem hamisítható

    @Test
    void actorComesFromTheTokenNotFromHeadersOrBody() throws Exception {
        String renter = login(mvc, RENTER);

        mvc.perform(post("/api/bikes").with(bearer(renter)).header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fejlec-01\",\"type\":\"city\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rentals").with(bearer(renter)).header("X-User-Id", "u3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bikeId\":\"b5\",\"userId\":\"u3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("u2"));
        mvc.perform(get("/api/rentals").with(bearer(renter)).header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("u2"))));
    }

    @Test
    void renterCanOnlyReturnOwnRentalButManagerCanReturnAny() throws Exception {
        String owner = login(mvc, OTHER_RENTER);
        String stranger = login(mvc, RENTER);
        String manager = login(mvc, MANAGER);

        mvc.perform(post("/api/rentals/r2/return").with(bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_RENTAL_OWNER"));
        mvc.perform(post("/api/rentals/r2/return").with(bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u3"));
        mvc.perform(post("/api/rentals/r2/return").with(bearer(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerCanReturnOwnRental() throws Exception {
        String owner = login(mvc, RENTER);
        String manager = login(mvc, MANAGER);
        String created = mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Owner-" + UUID.randomUUID().toString().substring(0, 8)
                                + "\",\"type\":\"city\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bikeId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        String rented = mvc.perform(post("/api/rentals").with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"" + bikeId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(rented, "$.id");

        mvc.perform(post("/api/rentals/" + id + "/return").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").isNumber());
    }

    // ---------------------------------------------------------------- átviteli védelem

    @Test
    void responsesAreNotCacheableAndHardenedAndStatelessWithoutCookies() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(TestAuth.loginJson(RENTER, TestAuth.DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getCookies()).isEmpty();
        assertThat(result.getRequest().getSession(false)).as("nem jön létre session").isNull();
    }

    @Test
    void corsAllowsOnlyTheConfiguredOriginAndTheAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/bikes").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/bikes").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bikes").header("Origin", "http://evil.example").with(bearer(login(mvc, RENTER))))
                .andExpect(status().isForbidden());
    }
}
