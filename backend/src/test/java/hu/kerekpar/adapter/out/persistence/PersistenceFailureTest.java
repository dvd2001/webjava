package hu.kerekpar.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import hu.kerekpar.application.port.in.RentBikeUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hibás esetek valódi PostgreSQL-en: az adatbázis constraintje dönt, és a végállapotot is ellenőrizzük.
 * Az osztály saját adatbázist kap; a tesztek egyedi biciklikkel dolgoznak, ezért nem takarítanak és nem függenek egymástól.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("postgres")
@TestMethodOrder(MethodOrderer.Random.class)
class PersistenceFailureTest {

    private static final Logger log = LoggerFactory.getLogger(PersistenceFailureTest.class);
    private static final Instant START = Instant.parse("2026-03-14T09:30:00Z");

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDatabase(registry, EmbeddedPostgresSupport.createEmptyDatabase());
    }

    @Autowired
    private BikeRepository bikes;
    @Autowired
    private RentalRepository rentals;
    @Autowired
    private RentBikeUseCase rentBike;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate tx;

    @BeforeEach
    void transactionTemplate() {
        tx = new TransactionTemplate(transactionManager);
    }

    @Test
    void duplicateActiveRentalOfTheSameBikeIsRejectedByTheDatabaseConstraintOnFlush() {
        Bike bike = bikes.save(Bike.register(bikes.nextId(), "City-31", BikeType.CITY));
        long bikeKey = EntityIds.require(EntityIds.BIKE, bike.id());
        Rental first = rentals.save(Rental.start(rentals.nextId(), bike.id(), "u2", START));
        log.info("ELŐTTE (duplikátum): {}", rentalRows(bikeKey));
        assertThat(rentalRows(bikeKey)).hasSize(1);

        // ugyanaz a bicikli még egyszer aktív bérlésként; a Hibernate-et közvetlenül, a port fordítása nélkül használjuk
        long duplicateKey = EntityIds.require(EntityIds.RENTAL, rentals.nextId());
        Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(status -> {
            entityManager.persist(new RentalJpaEntity(duplicateKey, bikeKey, 3L, START.plusSeconds(60), null, null));
            entityManager.flush();
        }));
        log.info("KIVÉTEL (duplikátum): {}", NestedExceptionUtils.getMostSpecificCause(thrown).getMessage());

        assertThat(thrown).isNotNull();
        assertThat(NestedExceptionUtils.getRootCause(thrown)).isInstanceOfSatisfying(PSQLException.class, psql -> {
            assertThat(psql.getSQLState()).as("PostgreSQL unique_violation").isEqualTo("23505");
            assertThat(psql.getServerErrorMessage().getConstraint()).isEqualTo("uq_rental_one_active_per_bike");
            assertThat(psql.getServerErrorMessage().getTable()).isEqualTo("rental");
        });

        log.info("UTÁNA (duplikátum): {}", rentalRows(bikeKey));
        List<Map<String, Object>> after = rentalRows(bikeKey);
        assertThat(after).hasSize(1);
        assertThat(after.get(0)).containsEntry("id", EntityIds.require(EntityIds.RENTAL, first.id()))
                .containsEntry("user_id", 2L);
    }

    @Test
    void failureAfterTheBikeUpdateRollsTheWholeRentalBack() {
        Bike bike = bikes.save(Bike.register(bikes.nextId(), "City-32", BikeType.CITY));
        long bikeKey = EntityIds.require(EntityIds.BIKE, bike.id());
        // Hibás kiinduló állapot: a bicikli "elérhető", de már van aktív bérlése (pl. kézi adatjavítás vagy verseny után)
        long strayKey = EntityIds.require(EntityIds.RENTAL, rentals.nextId());
        jdbc.update("INSERT INTO rental (id, bike_id, user_id, started_at) VALUES (?, ?, ?, ?::timestamptz)",
                strayKey, bikeKey, 3L, "2026-03-14T08:00:00Z");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        String before = state(bikeKey);
        log.info("ELŐTTE (rollback): {}", before);
        assertThat(bikeRow(bikeKey)).containsEntry("status", "AVAILABLE").containsEntry("version", 0L);
        assertThat(rentalRows(bikeKey)).hasSize(1);
        long updatesBefore = statistics.getEntityUpdateCount();

        // A use case a bicikli IN_USE állapotát előbb menti (flush), a bérlés beszúrása a constraint miatt hibázik
        Throwable thrown = catchThrowable(() -> rentBike.execute(new RentBikeUseCase.Command("u2", bike.id())));
        log.info("KIVÉTEL (rollback): {} / {}", thrown.getClass().getSimpleName(), thrown.getMessage());

        assertThat(thrown).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.code()).isEqualTo("BIKE_NOT_AVAILABLE"));
        assertThat(statistics.getEntityUpdateCount() - updatesBefore)
                .as("a bicikli módosítása ténylegesen eljutott az adatbázisig a hiba előtt").isEqualTo(1);

        // Új tranzakcióból, a portokon át és nyers SQL-lel is: a félkész állapot nem maradhatott bent
        Bike reloaded = tx.execute(status -> bikes.findById(bike.id()).orElseThrow());
        String after = state(bikeKey);
        log.info("UTÁNA (rollback): {}", after);
        assertThat(reloaded.status()).isEqualTo(BikeStatus.AVAILABLE);
        assertThat(bikeRow(bikeKey)).containsEntry("status", "AVAILABLE").containsEntry("version", 0L);
        List<Map<String, Object>> rentalRows = rentalRows(bikeKey);
        assertThat(rentalRows).hasSize(1);
        assertThat(rentalRows.get(0)).containsEntry("id", strayKey).containsEntry("user_id", 3L);
        assertThat(after).isEqualTo(before);
    }

    private String state(long bikeKey) {
        return "bike=" + bikeRow(bikeKey) + ", rentals=" + rentalRows(bikeKey);
    }

    private Map<String, Object> bikeRow(long bikeKey) {
        return jdbc.queryForMap("SELECT status, version FROM bike WHERE id = ?", bikeKey);
    }

    private List<Map<String, Object>> rentalRows(long bikeKey) {
        return jdbc.queryForList(
                "SELECT id, user_id, ended_at IS NULL AS active, fee FROM rental WHERE bike_id = ? ORDER BY id", bikeKey);
    }
}
