package hu.kerekpar.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Bejelentkezés és Bearer fejléc a MockMvc alapú tesztekhez. */
public final class TestAuth {

    /** A fejlesztői példafelhasználók (anna, peter, eszter, gabor) közös jelszava. */
    public static final String DEMO_PASSWORD = "Demo-Kerekpar-2026";

    public static final String MANAGER = "anna";
    public static final String RENTER = "peter";
    public static final String OTHER_RENTER = "eszter";

    private TestAuth() {
    }

    public static String login(MockMvc mvc, String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    public static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    public static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
