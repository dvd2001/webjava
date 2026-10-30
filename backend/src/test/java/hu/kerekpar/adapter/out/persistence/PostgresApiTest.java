package hu.kerekpar.adapter.out.persistence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Ugyanaz az API a PostgreSQL adapterrel: a use case-ek és a REST réteg változatlanul működnek. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class PostgresApiTest {

    private static final String USER = "X-User-Id";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.register(registry);
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
    void fullRentalFlowOnPostgres() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("u1"));
        mvc.perform(get("/api/tariffs")).andExpect(jsonPath("$.electric.perMinute").value(35));
        mvc.perform(get("/api/bikes")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        String created = mvc.perform(post("/api/bikes").header(USER, "u1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"City-03\",\"type\":\"city\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("available"))
                .andReturn().getResponse().getContentAsString();
        String bikeId = created.replaceAll(".*\"id\":\"(b\\d+)\".*", "$1");

        mvc.perform(post("/api/bikes").header(USER, "u1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"city-03\",\"type\":\"city\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NAME_TAKEN"));
        mvc.perform(post("/api/bikes").header(USER, "u2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"City-04\",\"type\":\"city\"}"))
                .andExpect(status().isForbidden());

        String rented = mvc.perform(post("/api/rentals").header(USER, "u2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"" + bikeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bikeId").value(bikeId))
                .andReturn().getResponse().getContentAsString();
        String rentalId = rented.replaceAll(".*\"id\":\"(r\\d+)\".*", "$1");

        mvc.perform(post("/api/rentals").header(USER, "u3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"" + bikeId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NOT_AVAILABLE"));
        mvc.perform(patch("/api/bikes/" + bikeId + "/status").header(USER, "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"maintenance\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/bikes")).andExpect(jsonPath("$[0].status").value("in_use"));

        mvc.perform(post("/api/rentals/" + rentalId + "/return").header(USER, "u2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(315));
        mvc.perform(post("/api/rentals/" + rentalId + "/return").header(USER, "u2"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/bikes")).andExpect(jsonPath("$[0].status").value("available"));
        mvc.perform(get("/api/rentals").header(USER, "u2").param("status", "finished"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
