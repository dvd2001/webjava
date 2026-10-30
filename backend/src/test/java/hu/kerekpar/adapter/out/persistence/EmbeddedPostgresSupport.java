package hu.kerekpar.adapter.out.persistence;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Egy beágyazott PostgreSQL az egész teszt-JVM-nek (Docker nélkül is fut). */
final class EmbeddedPostgresSupport {

    private static final EmbeddedPostgres POSTGRES = start();

    private EmbeddedPostgresSupport() {
    }

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // a leállításkori hiba nem befolyásolja a teszt eredményét
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void register(DynamicPropertyRegistry registry) {
        registerDatabase(registry, url("postgres"));
    }

    /** Új, teljesen üres adatbázist hoz létre, ezért a Flyway migráció tiszta lappal indul. */
    static String createEmptyDatabase() {
        String name = "it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url("postgres"), "postgres", "postgres");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Nem hozható létre a tesztadatbázis", e);
        }
        return url(name);
    }

    static void registerDatabase(DynamicPropertyRegistry registry, String jdbcUrl) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    private static String url(String database) {
        return "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/" + database;
    }
}
