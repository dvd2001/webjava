package hu.kerekpar.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/** A futó alkalmazás pontosan az api/openapi.yaml-ban leírt végpontokat és védelmet valósítja meg. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Path SPEC = Path.of("..", "api", "openapi.yaml");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final String LOGIN = "POST /api/auth/login";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;
    @Autowired
    private MockMvc mvc;

    @Test
    void implementedApiEndpointsMatchTheSpecification() throws IOException {
        assertThat(implementedOperations()).isEqualTo(documentedOperations().keySet());
        assertThat(documentedOperations()).hasSize(10);
    }

    @Test
    void onlyTheLoginOperationIsDocumentedAsPublic() throws IOException {
        Map<String, Object> spec = loadSpec();
        assertThat(spec.get("security")).as("globális védelem").isNotNull();

        Set<String> publicOperations = new TreeSet<>();
        documentedOperations().forEach((operation, definition) -> {
            if (definition.containsKey("security") && ((java.util.List<?>) definition.get("security")).isEmpty()) {
                publicOperations.add(operation);
            }
        });
        assertThat(publicOperations).containsExactly(LOGIN);
    }

    @Test
    void everyDocumentedOperationExceptLoginRejectsAnonymousCallers() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (String operation : documentedOperations().keySet()) {
            if (operation.equals(LOGIN)) {
                continue;
            }
            String[] parts = operation.split(" ", 2);
            String path = parts[1].replaceAll("\\{[^}]+}", "x");
            int status = mvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(parts[0]), path))
                    .andReturn().getResponse().getStatus();
            softly.assertThat(status).as(operation).isEqualTo(401);
        }
        softly.assertAll();
    }

    @Test
    void everySecuredOperationDocumentsTheUnauthorizedResponse() throws IOException {
        SoftAssertions softly = new SoftAssertions();
        documentedOperations().forEach((operation, definition) -> {
            if (!operation.equals(LOGIN)) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> responses = (Map<Object, Object>) definition.get("responses");
                softly.assertThat(responses.keySet().stream().map(String::valueOf)).as(operation).contains("401");
            }
        });
        softly.assertAll();
    }

    private Map<String, Map<String, Object>> documentedOperations() throws IOException {
        Map<String, Map<String, Object>> operations = new TreeMap<>();
        Map<String, Map<String, Map<String, Object>>> paths = castPaths(loadSpec().get("paths"));
        paths.forEach((path, item) -> item.forEach((method, definition) -> {
            if (HTTP_METHODS.contains(method)) {
                operations.put(method.toUpperCase() + " " + path, definition);
            }
        }));
        return operations;
    }

    private Set<String> implementedOperations() {
        Set<String> operations = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            info.getPathPatternsCondition().getPatternValues().stream()
                    .filter(pattern -> pattern.startsWith("/api/"))
                    .forEach(pattern -> info.getMethodsCondition().getMethods()
                            .forEach(method -> operations.add(method.name() + " " + pattern)));
        }
        return operations;
    }

    private static Map<String, Object> loadSpec() throws IOException {
        try (Reader reader = Files.newBufferedReader(SPEC)) {
            return new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> castPaths(Object paths) {
        return (Map<String, Map<String, Map<String, Object>>>) paths;
    }
}
