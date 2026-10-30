package hu.kerekpar.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiTest {

    private static final String USER = "X-User-Id";

    @Autowired
    private MockMvc mvc;

    @Test
    void listsSeedBikesAndReferenceData() throws Exception {
        mvc.perform(get("/api/bikes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[1].status").value("in_use"));
        mvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$[0].role").value("manager"));
        mvc.perform(get("/api/tariffs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.electric.perMinute").value(35));
    }

    @Test
    void managerCreatesBikeAndRenterCannot() throws Exception {
        String body = "{\"name\":\"City-03\",\"type\":\"city\"}";

        mvc.perform(post("/api/bikes").header(USER, "u1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("available"))
                .andExpect(jsonPath("$.type").value("city"));
        mvc.perform(post("/api/bikes").header(USER, "u2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
        mvc.perform(post("/api/bikes").header(USER, "u1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rentAndReturnFlow() throws Exception {
        mvc.perform(post("/api/rentals").header(USER, "u2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"b3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bikeId").value("b3"))
                .andExpect(jsonPath("$.endedAt").doesNotExist());
        mvc.perform(post("/api/rentals").header(USER, "u3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":\"b3\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIKE_NOT_AVAILABLE"));
        mvc.perform(post("/api/rentals/r1/return").header(USER, "u2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").isNumber());
        mvc.perform(post("/api/rentals/r1/return").header(USER, "u2"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/rentals").header(USER, "u2").param("status", "finished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void statusChangeUsesPatch() throws Exception {
        mvc.perform(patch("/api/bikes/b1/status").header(USER, "u1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("maintenance"));
        mvc.perform(patch("/api/bikes/b2/status").header(USER, "u1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"available\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsGetConsistentErrors() throws Exception {
        mvc.perform(get("/api/rentals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/rentals").header(USER, "nincs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(get("/api/rentals").header(USER, "u1").param("status", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        mvc.perform(post("/api/bikes").header(USER, "u1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X-1\",\"type\":\"rakett\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/nincs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"));
    }
}
