package hu.kerekpar.adapter.in.web;

import static hu.kerekpar.support.TestAuth.MANAGER;
import static hu.kerekpar.support.TestAuth.bearer;
import static hu.kerekpar.support.TestAuth.login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** A hibás kérések a keretrendszer szintjén is a szerződés hibaformátumát kapják. */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void malformedJsonIsABadRequestWithTheContractError() throws Exception {
        String manager = login(mvc, MANAGER);

        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON).content("{nem json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingBodyIsABadRequest() throws Exception {
        String manager = login(mvc, MANAGER);

        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedMediaTypeIsReportedInTheContractFormat() throws Exception {
        String manager = login(mvc, MANAGER);

        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.TEXT_PLAIN).content("City-03"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"));
    }

    @Test
    void wrongTypedFieldsDoNotLeakJacksonInternals() throws Exception {
        String manager = login(mvc, MANAGER);

        mvc.perform(post("/api/bikes").with(bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":123456,\"type\":[\"city\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Érvénytelen kérés: hibás vagy hiányzó adat."));
    }
}
