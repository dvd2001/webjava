package hu.kerekpar.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/** A futó alkalmazás pontosan az api/openapi.yaml-ban leírt végpontokat szolgálja ki. */
@SpringBootTest
class OpenApiContractTest {

    private static final Path SPEC = Path.of("..", "api", "openapi.yaml");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void implementedApiEndpointsMatchTheSpecification() throws IOException {
        assertThat(implementedOperations()).isEqualTo(documentedOperations());
        assertThat(documentedOperations()).hasSize(8);
    }

    private static Set<String> documentedOperations() throws IOException {
        Set<String> operations = new TreeSet<>();
        try (Reader reader = Files.newBufferedReader(SPEC)) {
            Map<String, Object> spec = new Yaml().load(reader);
            Map<String, Map<String, Object>> paths = castPaths(spec.get("paths"));
            paths.forEach((path, item) -> item.keySet().stream()
                    .filter(HTTP_METHODS::contains)
                    .forEach(method -> operations.add(method.toUpperCase() + " " + path)));
        }
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

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> castPaths(Object paths) {
        return (Map<String, Map<String, Object>>) paths;
    }
}
