package hu.kerekpar.adapter.out.persistence;

import static hu.kerekpar.support.TestAuth.DEMO_PASSWORD;
import static hu.kerekpar.support.TestAuth.MANAGER;
import static hu.kerekpar.support.TestAuth.OTHER_RENTER;
import static hu.kerekpar.support.TestAuth.RENTER;
import static hu.kerekpar.support.TestAuth.bearer;
import static hu.kerekpar.support.TestAuth.login;
import static hu.kerekpar.support.TestAuth.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hu.kerekpar.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Ugyanaz az API a PostgreSQL adapterrel és az adatbázisban tárolt jelszavakkal, bejelentkezéssel és szerepkörökkel. */
@SpringBootTest(properties = "kerekpar.security.jwt-secret=" + TestTokens.SECRET)
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class PostgresApiTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDatabase(registry, EmbeddedPostgresSupport.createEmptyDatabase());
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM rental");
        jdbc.update("DELETE FROM bike");
    }

    @Test
    void loginUsesThePasswordsFromTheDatabase() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("peter", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("u2"))
                .andExpect(jsonPath("$.user.role").value("renter"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("peter", "rossz")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nincs-ilyen", DEMO_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutAStoredPasswordCannotLogIn() throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = NULL WHERE username = 'gabor'");
        try {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("gabor", DEMO_PASSWORD)))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("UPDATE app_user SET password_hash = (SELECT password_hash FROM app_user WHERE username = 'anna') "
                    + "WHERE username = 'gabor'");
        }
    }

    @Test
    void protectedEndpointsRequireAValidTokenOnPostgresToo() throws Exception {
        mvc.perform(get("/api/bikes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").with(bearer(login(mvc, RENTER)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(bearer(login(mvc, MANAGER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("u1"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].username").doesNotExist());
    }

    @Test
    void fullRentalFlowOnPostgres() throws Exception {
        String manager = login(mvc, MANAGER);
        String renter = login(mvc, RENTER);
        String otherRenter = login(mvc, OTHER_RENTER);

        mvc.perform(get("/api/tariffs").with(bearer(renter))).andExpect(jsonPath("$.electric.perMinute").value(35));
        mvc.perform(get("/api/bikes").with(bearer(renter))).andExpect(jsonPath("$.length()").value(0));

        String created = mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"City-03\",\"type\":\"city\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("available"))
                .andReturn().getResponse().getContentAsString();
        String bikeId = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"city-03\",\"type\":\"city\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NAME_TAKEN"));
        mvc.perform(post("/api/bikes").with(bearer(renter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"City-04\",\"type\":\"city\"}"))
                .andExpect(status().isForbidden());

        String rented = mvc.perform(post("/api/rentals").with(bearer(renter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"" + bikeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("u2"))
                .andReturn().getResponse().getContentAsString();
        String rentalId = JsonPath.read(rented, "$.id");

        mvc.perform(post("/api/rentals").with(bearer(otherRenter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"" + bikeId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NOT_AVAILABLE"));
        mvc.perform(patch("/api/bikes/" + bikeId + "/status").with(bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"maintenance\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/rentals/" + rentalId + "/return").with(bearer(otherRenter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_RENTAL_OWNER"));

        mvc.perform(post("/api/rentals/" + rentalId + "/return").with(bearer(renter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(315));
        mvc.perform(post("/api/rentals/" + rentalId + "/return").with(bearer(renter)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/bikes").with(bearer(renter))).andExpect(jsonPath("$[0].status").value("available"));
        mvc.perform(get("/api/rentals").with(bearer(renter)).param("status", "finished"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/rentals").with(bearer(otherRenter)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
