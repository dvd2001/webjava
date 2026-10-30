package hu.kerekpar.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("postgres")
class PostgresAdapterTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.register(registry);
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
    private UnitOfWork unitOfWork;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM rental");
        jdbc.update("DELETE FROM bike");
    }

    private Bike newBike(String name, BikeType type) {
        return bikes.save(Bike.register(bikes.nextId(), name, type));
    }

    @Test
    void usersAndTariffsComeFromMigrations() {
        assertThat(users.findAll()).containsExactly(
                new User("u1", "Kovács Anna", UserRole.MANAGER),
                new User("u2", "Nagy Péter", UserRole.RENTER),
                new User("u3", "Szabó Eszter", UserRole.RENTER),
                new User("u4", "Tóth Gábor", UserRole.RENTER));
        assertThat(users.findById("u1")).get().extracting(User::isManager).isEqualTo(true);
        assertThat(users.findById("u999")).isEmpty();
        assertThat(tariffs.findAll()).containsEntry(BikeType.ELECTRIC, new Tariff(500, 35)).hasSize(3);
        assertThat(tariffs.findByType(BikeType.CITY)).contains(new Tariff(300, 15));
    }

    @Test
    void bikeRoundTripKeepsDomainIdsAndFields() {
        Bike saved = newBike("City-03", BikeType.CITY);

        assertThat(saved.id()).matches("b\\d+");
        assertThat(saved.status()).isEqualTo(BikeStatus.AVAILABLE);
        assertThat(bikes.findById(saved.id())).contains(saved);
        assertThat(bikes.findAll()).containsExactly(saved);

        Bike updated = bikes.save(saved.changeStatus(BikeStatus.MAINTENANCE));
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(bikes.findById(saved.id())).get().extracting(Bike::status).isEqualTo(BikeStatus.MAINTENANCE);
    }

    @Test
    void unknownOrMalformedIdsAreNotFound() {
        assertThat(bikes.findById("b999999")).isEmpty();
        assertThat(bikes.findById("x1")).isEmpty();
        assertThat(bikes.findById("b")).isEmpty();
        assertThat(bikes.findById(null)).isEmpty();
        assertThat(rentals.findById("r-1")).isEmpty();
    }

    @Test
    void nameLookupIgnoresCaseAndDuplicateIsConflict() {
        newBike("City-03", BikeType.CITY);

        assertThat(bikes.existsByName("city-03")).isTrue();
        assertThat(bikes.existsByName("City-04")).isFalse();
        assertThatThrownBy(() -> newBike("CITY-03", BikeType.CITY))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("BIKE_NAME_TAKEN"));
    }

    @Test
    void rentalRoundTripKeepsInstantsAndFee() {
        Bike bike = newBike("City-03", BikeType.CITY);
        Instant start = Instant.parse("2026-01-01T10:00:00.123456Z");
        Instant end = start.plusSeconds(600);

        Rental active = rentals.save(Rental.start(rentals.nextId(), bike.id(), "u2", start));
        assertThat(active.isActive()).isTrue();
        assertThat(rentals.findById(active.id())).contains(active);

        Rental finished = rentals.save(active.finish(end, 450));
        assertThat(finished).isEqualTo(new Rental(active.id(), bike.id(), "u2", start, end, 450L));
        assertThat(rentals.findAll()).containsExactly(finished);
    }

    @Test
    void secondActiveRentalOfSameBikeIsConflict() {
        Bike bike = newBike("City-03", BikeType.CITY);
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        rentals.save(Rental.start(rentals.nextId(), bike.id(), "u2", now));

        assertThatThrownBy(() -> rentals.save(Rental.start(rentals.nextId(), bike.id(), "u3", now)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("BIKE_NOT_AVAILABLE"));
    }

    @Test
    void unitOfWorkRollsBackEverythingOnFailure() {
        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            newBike("City-03", BikeType.CITY);
            throw new IllegalStateException("szándékos hiba");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(bikes.findAll()).isEmpty();
    }

    @Test
    void concurrentStatusChangesAllowOnlyOneWriter() throws Exception {
        Bike bike = newBike("City-03", BikeType.CITY);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        Callable<Bike> change = () -> unitOfWork.execute(() -> {
            Bike read = bikes.findById(bike.id()).orElseThrow();
            await(bothRead);
            return bikes.save(read.changeStatus(BikeStatus.MAINTENANCE));
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Bike>> results = List.of(pool.submit(change), pool.submit(change));
            int succeeded = 0;
            int conflicts = 0;
            for (Future<Bike> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfSatisfying(ConflictException.class,
                            c -> assertThat(c.code()).isEqualTo("CONCURRENT_MODIFICATION"));
                    conflicts++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
