package hu.kerekpar.adapter.in.web;

import static hu.kerekpar.support.TestAuth.MANAGER;
import static hu.kerekpar.support.TestAuth.OTHER_RENTER;
import static hu.kerekpar.support.TestAuth.RENTER;
import static hu.kerekpar.support.TestAuth.bearer;
import static hu.kerekpar.support.TestAuth.login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Az üzleti folyamatok HTTP-n át, bejelentkezett felhasználókkal (memóriabeli tárolóval). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiTest {

    @Autowired
    private MockMvc mvc;

    private String manager;
    private String renter;
    private String otherRenter;

    @BeforeEach
    void logIn() throws Exception {
        manager = login(mvc, MANAGER);
        renter = login(mvc, RENTER);
        otherRenter = login(mvc, OTHER_RENTER);
    }

    @Test
    void listsSeedBikesAndReferenceData() throws Exception {
        mvc.perform(get("/api/bikes").with(bearer(renter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[1].status").value("in_use"));
        mvc.perform(get("/api/users").with(bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("manager"))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/tariffs").with(bearer(renter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.electric.perMinute").value(35));
    }

    @Test
    void managerCreatesBikeAndRenterCannot() throws Exception {
        String body = "{\"name\":\"City-03\",\"type\":\"city\"}";

        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("available"))
                .andExpect(jsonPath("$.type").value("city"));
        mvc.perform(post("/api/bikes").with(bearer(renter)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NAME_TAKEN"));
    }

    @Test
    void rentAndReturnFlow() throws Exception {
        mvc.perform(post("/api/rentals").with(bearer(renter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"b3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bikeId").value("b3"))
                .andExpect(jsonPath("$.userId").value("u2"))
                .andExpect(jsonPath("$.endedAt").doesNotExist());
        mvc.perform(post("/api/rentals").with(bearer(otherRenter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"b3\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NOT_AVAILABLE"));
        mvc.perform(post("/api/rentals/r1/return").with(bearer(renter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").isNumber());
        mvc.perform(post("/api/rentals/r1/return").with(bearer(renter)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/rentals").with(bearer(renter)).param("status", "finished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rentalListIsScopedToTheCallerAndFilteredByStatus() throws Exception {
        mvc.perform(get("/api/rentals").with(bearer(manager)))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/rentals").with(bearer(manager)).param("status", "active"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/rentals").with(bearer(renter)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("u2"));
        mvc.perform(get("/api/rentals").with(bearer(otherRenter)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("u3"));
    }

    @Test
    void statusChangeUsesPatch() throws Exception {
        mvc.perform(patch("/api/bikes/b1/status").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("maintenance"));
        mvc.perform(patch("/api/bikes/b2/status").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"available\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/bikes/b999/status").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"available\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIKE_NOT_FOUND"));
    }

    @Test
    void invalidRequestsGetConsistentErrors() throws Exception {
        mvc.perform(get("/api/rentals").with(bearer(manager)).param("status", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X-1\",\"type\":\"rakett\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"city\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIKE_NAME"));
        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"City-77\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIKE_TYPE"));
        mvc.perform(post("/api/rentals").with(bearer(renter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"b999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIKE_NOT_FOUND"));
        mvc.perform(post("/api/rentals/r999/return").with(bearer(renter)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RENTAL_NOT_FOUND"));
    }
}
