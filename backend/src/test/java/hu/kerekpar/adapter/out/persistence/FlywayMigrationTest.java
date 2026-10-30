package hu.kerekpar.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Valódi PostgreSQL-lel, Spring nélkül: a migrációk egy üres adatbázison futnak le. */
class FlywayMigrationTest {

    @Test
    void migrationsRunOnEmptyDatabase() {
        String url = EmbeddedPostgresSupport.createEmptyDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "postgres"));
        assertThat(publicTables(jdbc)).as("a tesztadatbázis indulásakor üres").isEmpty();

        Flyway flyway = Flyway.configure().dataSource(url, "postgres", "postgres").load();
        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(3);

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3");
        assertThat(history).extracting(row -> row.get("success")).containsOnly(true);

        assertThat(publicTables(jdbc))
                .containsExactlyInAnyOrder("app_user", "bike", "flyway_schema_history", "rental", "tariff");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tariff", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bike", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND column_name = 'version' AND table_name IN ('bike', 'rental')", Integer.class))
                .as("a V3 verzió-oszlopai").isEqualTo(2);

        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).as("újrafuttatás nem módosít semmit").isZero();
    }

    private static List<String> publicTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
                String.class);
    }
}
