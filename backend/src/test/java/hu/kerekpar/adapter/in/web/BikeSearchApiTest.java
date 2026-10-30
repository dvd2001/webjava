package hu.kerekpar.adapter.in.web;

import static hu.kerekpar.support.TestAuth.MANAGER;
import static hu.kerekpar.support.TestAuth.RENTER;
import static hu.kerekpar.support.TestAuth.bearer;
import static hu.kerekpar.support.TestAuth.login;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.kerekpar.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A bicikli-keresés HTTP-n át. Kiinduló adat (memóriabeli demo): City-01 (elérhető), City-02 (használatban),
 * Hegyi-01 (elérhető), Hegyi-02 (karbantartás), E-bike-01 (elérhető), E-bike-02 (használatban).
 */
@SpringBootTest(properties = "kerekpar.security.jwt-secret=" + TestTokens.SECRET)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BikeSearchApiTest {

    @Autowired
    private MockMvc mvc;

    private String renter;
    private String manager;

    @BeforeEach
    void logIn() throws Exception {
        renter = login(mvc, RENTER);
        manager = login(mvc, MANAGER);
    }

    @Test
    void withoutCriteriaEveryBikeIsReturnedSortedByName() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[*].name",
                        contains("City-01", "City-02", "E-bike-01", "E-bike-02", "Hegyi-01", "Hegyi-02")));
    }

    @Test
    void bothRolesCanSearchAndGetTheSameData() throws Exception {
        for (String token : new String[] {renter, manager}) {
            mvc.perform(get("/api/bikes/search").with(bearer(token)).param("q", "city"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains("b1", "b2")))
                    .andExpect(jsonPath("$.items[0].type").value("city"))
                    .andExpect(jsonPath("$.items[0].status").value("available"))
                    .andExpect(jsonPath("$.items[1].status").value("in_use"));
        }
    }

    @Test
    void nameSearchIsCaseInsensitiveAndTrimmed() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "  E-BIKE "))
                .andExpect(jsonPath("$.items[*].name", contains("E-bike-01", "E-bike-02")))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void typeAndStatusFiltersUseTheContractValues() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("type", "electric"))
                .andExpect(jsonPath("$.items[*].name", contains("E-bike-01", "E-bike-02")));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("status", "in_use"))
                .andExpect(jsonPath("$.items[*].name", contains("City-02", "E-bike-02")));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("status", "maintenance"))
                .andExpect(jsonPath("$.items[*].name", contains("Hegyi-02")));
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter))
                        .param("q", "e-bike").param("type", "electric").param("status", "available"))
                .andExpect(jsonPath("$.items[*].name", contains("E-bike-01")))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "city").param("type", "mountain"))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void blankParametersMeanNoFilter() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "  ").param("type", "").param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));
    }

    @Test
    void limitTruncatesTheItemsButNotTheTotal() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("limit", "2"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[*].name", contains("City-01", "City-02")));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void specialCharactersAreSearchedLiterally() throws Exception {
        String created = "{\"name\":\"Teszt_50%\",\"type\":\"city\"}";
        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON).content(created))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "%"))
                .andExpect(jsonPath("$.items[*].name", contains("Teszt_50%")));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "_50"))
                .andExpect(jsonPath("$.items[*].name", contains("Teszt_50%")));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "'; DROP TABLE bike; --"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/bikes").with(bearer(renter))).andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    void searchDoesNotChangeAnything() throws Exception {
        String before = mvc.perform(get("/api/bikes").with(bearer(renter))).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/bikes/search").with(bearer(manager)).param("q", "city").param("status", "available"))
                .andExpect(status().isOk());

        String after = mvc.perform(get("/api/bikes").with(bearer(renter))).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    @Test
    void unknownTypeOrStatusIsABadRequestWithItsOwnCode() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("type", "rakett"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIKE_TYPE"));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("type", "CITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIKE_TYPE"));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("status", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void invalidLimitOrTooLongTextIsRejected() throws Exception {
        for (String limit : new String[] {"0", "-5", "101", "999999999999"}) {
            mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("limit", limit))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("limit", "0"))
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("q", "x".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_TEXT"));
    }

    @Test
    void nonNumericLimitIsAContractErrorWithoutLeakingInternals() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Érvénytelen paraméter: limit"));
    }

    @Test
    void anonymousAndInvalidTokensAreRejectedBeforeAnySearch() throws Exception {
        mvc.perform(get("/api/bikes/search")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/bikes/search").param("q", "city").with(bearer(TestTokens.unsigned("u1", "MANAGER"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenOfAnUnknownUserIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(TestTokens.valid("u999", "RENTER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ACTOR"));
    }

    @Test
    void onlyGetIsAllowedOnTheSearchPath() throws Exception {
        mvc.perform(post("/api/bikes/search").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actorHeaderAndBodyCannotChangeWhoSearches() throws Exception {
        mvc.perform(get("/api/bikes/search").with(bearer(renter)).header("X-User-Id", "u999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));
    }
}
