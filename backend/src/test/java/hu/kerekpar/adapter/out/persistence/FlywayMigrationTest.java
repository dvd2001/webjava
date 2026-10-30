package hu.kerekpar.adapter.out.persistence;

import static hu.kerekpar.support.TestAuth.DEMO_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Valódi PostgreSQL-lel, Spring nélkül: a migrációk egy üres adatbázison futnak le. */
class FlywayMigrationTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String DEV = "classpath:db/dev";

    private record Database(String url, JdbcTemplate jdbc) {
    }

    private static Database emptyDatabase() {
        String url = EmbeddedPostgresSupport.createEmptyDatabase();
        return new Database(url, new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "postgres")));
    }

    private static Flyway flyway(Database database, String... locations) {
        return Flyway.configure().dataSource(database.url(), "postgres", "postgres").locations(locations).load();
    }

    private static List<String> publicTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
                String.class);
    }

    @Test
    void migrationsRunOnEmptyDatabase() {
        Database database = emptyDatabase();
        JdbcTemplate jdbc = database.jdbc();
        assertThat(publicTables(jdbc)).as("a tesztadatbázis indulásakor üres").isEmpty();

        Flyway flyway = flyway(database, MAIN);
        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(4);

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4");
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

    @Test
    void usersGetUsernamesButNoPasswordFromTheProductionMigrations() {
        Database database = emptyDatabase();
        flyway(database, MAIN).migrate();

        List<Map<String, Object>> users = database.jdbc().queryForList(
                "SELECT username, password_hash FROM app_user ORDER BY id");

        assertThat(users).extracting(row -> row.get("username")).containsExactly("anna", "peter", "eszter", "gabor");
        assertThat(users).extracting(row -> row.get("password_hash")).containsOnlyNulls();
    }

    @Test
    void devLocationAddsRepeatableDemoPasswordsThatMatchTheDemoPassword() {
        Database database = emptyDatabase();
        Flyway flyway = flyway(database, MAIN, DEV);

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).as("4 verziózott + 1 ismételhető").isEqualTo(5);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        List<String> hashes = database.jdbc().queryForList("SELECT password_hash FROM app_user ORDER BY id", String.class);
        assertThat(hashes).hasSize(4).allSatisfy(hash -> assertThat(encoder.matches(DEMO_PASSWORD, hash)).isTrue());
        assertThat(hashes).as("minden felhasználónak ugyanaz a demo hash, jelszó nem kerül tárolásra szövegesen")
                .doesNotContain(DEMO_PASSWORD);
        assertThat(flyway.migrate().migrationsExecuted).as("az ismételhető migráció változatlanul nem fut újra").isZero();
    }

    @Test
    void repeatableDemoMigrationDoesNotOverwriteAnAlreadySetPassword() {
        Database database = emptyDatabase();
        flyway(database, MAIN, DEV).migrate();
        String own = new BCryptPasswordEncoder().encode("Sajat-jelszo-1");
        database.jdbc().update("UPDATE app_user SET password_hash = ? WHERE username = 'anna'", own);
        database.jdbc().update("UPDATE flyway_schema_history SET checksum = 0 WHERE version IS NULL");

        flyway(database, MAIN, DEV).migrate();

        assertThat(database.jdbc().queryForObject("SELECT password_hash FROM app_user WHERE username = 'anna'",
                String.class)).isEqualTo(own);
    }

    @Test
    void usernameAndPasswordHashConstraintsRejectBadData() {
        Database database = emptyDatabase();
        flyway(database, MAIN).migrate();
        JdbcTemplate jdbc = database.jdbc();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_user (name, role, username) VALUES ('X', 'RENTER', 'anna')"))
                .as("egyedi felhasználónév").isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_app_user_username");
        for (String bad : List.of("Ab", "ab", "UPPERCASE", "szóköz név", "ékezetes-é")) {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO app_user (name, role, username) VALUES ('X', 'RENTER', ?)", bad))
                    .as("felhasználónév: " + bad).hasMessageContaining("ck_app_user_username_format");
        }
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_user (name, role, username) VALUES ('X', 'RENTER', ?)", "x".repeat(51)))
                .as("az 50 karakternél hosszabb név az oszlop miatt sem fér be")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_user (name, role, username, password_hash) VALUES ('X', 'RENTER', 'xavier', 'sima-szoveg')"))
                .as("a jelszó csak BCrypt hash lehet").hasMessageContaining("ck_app_user_password_hash_bcrypt");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_user (name, role) VALUES ('X', 'RENTER')"))
                .as("a felhasználónév kötelező").isInstanceOf(DataIntegrityViolationException.class);
    }
}
