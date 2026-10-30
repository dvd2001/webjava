package hu.kerekpar.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Egy bérlés a hozzá tartozó biciklivel és bérlővel, valódi PostgreSQL-en át.
 * Az osztály saját, üres adatbázist kap (a Flyway indításkor migrál), a tesztek véletlen sorrendben is futnak.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("postgres")
@TestMethodOrder(MethodOrderer.Random.class)
class RentalRoundTripTest {

    private static final Instant START = Instant.parse("2026-03-14T09:30:00.123456Z");
    private static final Instant END = START.plusSeconds(25 * 60);

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDatabase(registry, EmbeddedPostgresSupport.createEmptyDatabase());
    }

    @Autowired
    private BikeRepository bikes;
    @Autowired
    private RentalRepository rentals;
    @Autowired
    private UserRepository users;
    @Autowired
    private TariffRepository tariffs;
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
    void cleanTables() {
        tx = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM rental");
        jdbc.update("DELETE FROM bike");
    }

    @Test
    void contextStartedWithMigratedSchemaAndSeededRenter() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(3);
        assertThat(users.findById("u2")).contains(new User("u2", "Nagy Péter", UserRole.RENTER));
    }

    @Test
    void rentalWithBikeAndRenterIsReadBackFromDatabaseAfterFlushAndClear() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        User renter = users.findById("u2").orElseThrow();
        Tariff cityTariff = tariffs.findByType(BikeType.CITY).orElseThrow();

        tx.executeWithoutResult(status -> {
            // az "eredeti" domain objektumok még mentés előtt jönnek létre, a szerver által kiosztott azonosítókkal
            Bike bike = Bike.register(bikes.nextId(), "City-07", BikeType.CITY);
            Rental started = Rental.start(rentals.nextId(), bike.id(), renter.id(), START);
            Bike rentedBike = bike.rent();
            Rental finished = started.finish(END, cityTariff.feeFor(START, END));
            Bike returnedBike = rentedBike.giveBack();

            // 1. bérlés indul: mentés, flush, a persistence context teljes ürítése
            bikes.save(bike);
            bikes.save(rentedBike);
            rentals.save(started);
            entityManager.flush();
            entityManager.clear();
            assertThat(entityManager.unwrap(Session.class).getStatistics().getEntityCount())
                    .as("a persistence context üres, az első szintű cache nem segíthet").isZero();

            long loadsBefore = statistics.getEntityLoadCount();
            Rental reloadedStarted = rentals.findById(started.id()).orElseThrow();
            Bike reloadedBike = bikes.findById(bike.id()).orElseThrow();
            assertThat(statistics.getEntityLoadCount() - loadsBefore)
                    .as("mindkét sor az adatbázisból töltődött be").isEqualTo(2);
            assertThat(reloadedStarted).isEqualTo(started);
            assertThat(reloadedBike).isEqualTo(rentedBike);
            assertThat(reloadedBike.status()).isEqualTo(BikeStatus.IN_USE);

            // 2. visszavétel: módosítás, flush, ürítés, újraolvasás
            rentals.save(finished);
            bikes.save(returnedBike);
            entityManager.flush();
            entityManager.clear();
            assertThat(entityManager.unwrap(Session.class).getStatistics().getEntityCount()).isZero();

            loadsBefore = statistics.getEntityLoadCount();
            Rental reloadedFinished = rentals.findById(finished.id()).orElseThrow();
            Bike reloadedReturned = bikes.findById(bike.id()).orElseThrow();
            assertThat(statistics.getEntityLoadCount() - loadsBefore).isEqualTo(2);
            assertThat(reloadedFinished).isEqualTo(finished);
            assertThat(reloadedFinished.fee()).isEqualTo(300 + 25 * 15);
            assertThat(reloadedReturned).isEqualTo(returnedBike);
            assertThat(reloadedReturned.status()).isEqualTo(BikeStatus.AVAILABLE);
        });
    }

    @Test
    void committedRentalIsVisibleInTheTablesAndEqualToTheOriginal() {
        Bike bike = Bike.register(bikes.nextId(), "Hegyi-05", BikeType.MOUNTAIN);
        Rental started = Rental.start(rentals.nextId(), bike.id(), "u3", START);
        Rental finished = started.finish(END, new Tariff(400, 20).feeFor(START, END));

        tx.executeWithoutResult(status -> {
            bikes.save(bike);
            rentals.save(started);
            rentals.save(finished);
        });

        long rentalKey = EntityIds.require(EntityIds.RENTAL, finished.id());
        long bikeKey = EntityIds.require(EntityIds.BIKE, bike.id());
        assertThat(jdbc.queryForObject("SELECT bike_id FROM rental WHERE id = ?", Long.class, rentalKey))
                .isEqualTo(bikeKey);
        assertThat(jdbc.queryForObject("SELECT user_id FROM rental WHERE id = ?", Long.class, rentalKey))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT started_at FROM rental WHERE id = ?", OffsetDateTime.class, rentalKey)
                .toInstant()).isEqualTo(START);
        assertThat(jdbc.queryForObject("SELECT ended_at FROM rental WHERE id = ?", OffsetDateTime.class, rentalKey)
                .toInstant()).isEqualTo(END);
        assertThat(jdbc.queryForObject("SELECT fee FROM rental WHERE id = ?", Long.class, rentalKey))
                .isEqualTo(400 + 25 * 20);
        assertThat(jdbc.queryForObject("SELECT type FROM bike WHERE id = ?", String.class, bikeKey))
                .isEqualTo("MOUNTAIN");

        // új tranzakció, új persistence context: semmi sem származhat a korábbi cache-ből
        Rental reloaded = tx.execute(status -> rentals.findById(finished.id()).orElseThrow());
        assertThat(reloaded).isEqualTo(finished);
        assertThat(bikes.findById(bike.id())).contains(bike);
    }
}
