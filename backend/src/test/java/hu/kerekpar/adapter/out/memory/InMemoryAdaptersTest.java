package hu.kerekpar.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.adapter.out.security.BcryptPasswordHasher;
import hu.kerekpar.application.port.out.CredentialsRepository.UserCredentials;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.UserRole;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryAdaptersTest {

    @Test
    void bikeRepositoryStoresInInsertionOrderAndOverwritesBySameId() {
        InMemoryBikeRepository repository = new InMemoryBikeRepository();
        Bike first = repository.save(new Bike("b2", "B", BikeType.CITY, BikeStatus.AVAILABLE));
        repository.save(new Bike("b1", "A", BikeType.CITY, BikeStatus.AVAILABLE));
        repository.save(new Bike("b2", "B-újra", BikeType.CITY, BikeStatus.MAINTENANCE));

        assertThat(first.id()).isEqualTo("b2");
        assertThat(repository.findAll()).extracting(Bike::id).containsExactly("b2", "b1");
        assertThat(repository.findById("b2")).get().extracting(Bike::name).isEqualTo("B-újra");
        assertThat(repository.findById("nincs")).isEmpty();
    }

    @Test
    void bikeNameLookupIgnoresCase() {
        InMemoryBikeRepository repository = new InMemoryBikeRepository();
        repository.save(new Bike("b1", "City-01", BikeType.CITY, BikeStatus.AVAILABLE));

        assertThat(repository.existsByName("CITY-01")).isTrue();
        assertThat(repository.existsByName("city-02")).isFalse();
    }

    @Test
    void findAllReturnsACopyThatCannotChangeTheStore() {
        InMemoryBikeRepository repository = new InMemoryBikeRepository();
        repository.save(new Bike("b1", "City-01", BikeType.CITY, BikeStatus.AVAILABLE));

        repository.findAll().clear();

        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void generatedIdsAreUniqueAndSkipExistingOnes() {
        InMemoryBikeRepository bikes = new InMemoryBikeRepository();
        bikes.save(new Bike("b1", "A", BikeType.CITY, BikeStatus.AVAILABLE));
        InMemoryRentalRepository rentals = new InMemoryRentalRepository();
        rentals.save(Rental.start("r1", "b1", "u2", Instant.EPOCH));

        Set<String> bikeIds = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            bikeIds.add(bikes.nextId());
        }

        assertThat(bikeIds).hasSize(20).doesNotContain("b1").allMatch(id -> id.startsWith("b"));
        assertThat(rentals.nextId()).isEqualTo("r2");
    }

    @Test
    void rentalRepositoryStoresAndFinds() {
        InMemoryRentalRepository repository = new InMemoryRentalRepository();
        Rental active = repository.save(Rental.start("r1", "b1", "u2", Instant.EPOCH));
        repository.save(active.finish(Instant.EPOCH.plusSeconds(60), 330));

        assertThat(repository.findById("r1")).get().extracting(Rental::fee).isEqualTo(330L);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById("r9")).isEmpty();
    }

    @Test
    void userRepositoryHasTheFourDemoUsers() {
        InMemoryUserRepository repository = new InMemoryUserRepository();

        assertThat(repository.findAll()).extracting(u -> u.id() + ":" + u.role())
                .containsExactly("u1:MANAGER", "u2:RENTER", "u3:RENTER", "u4:RENTER");
        assertThat(repository.findById("u3")).get().extracting(u -> u.name()).isEqualTo("Szabó Eszter");
        assertThat(repository.findById("u9")).isEmpty();
    }

    @Test
    void tariffRepositoryHasAnUnmodifiableTariffPerType() {
        InMemoryTariffRepository repository = new InMemoryTariffRepository();

        assertThat(repository.findAll()).containsOnlyKeys(BikeType.values());
        assertThat(repository.findByType(BikeType.MOUNTAIN)).contains(new Tariff(400, 20));
        assertThatThrownBy(() -> repository.findAll().put(BikeType.CITY, new Tariff(1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void credentialsRepositoryKnowsTheDemoUsersAndStoresOnlyAHash() {
        BcryptPasswordHasher hasher = new BcryptPasswordHasher(4);
        InMemoryCredentialsRepository repository =
                new InMemoryCredentialsRepository(new InMemoryUserRepository(), hasher, "Demo-Teszt-1");

        UserCredentials anna = repository.findByUsername("anna").orElseThrow();
        assertThat(anna.user().role()).isEqualTo(UserRole.MANAGER);
        assertThat(anna.passwordHash()).isNotEqualTo("Demo-Teszt-1").startsWith("$2");
        assertThat(hasher.matches("Demo-Teszt-1", anna.passwordHash())).isTrue();
        assertThat(repository.findByUsername("peter").orElseThrow().user().id()).isEqualTo("u2");
        assertThat(repository.findByUsername("eszter")).isPresent();
        assertThat(repository.findByUsername("gabor")).isPresent();
        assertThat(repository.findByUsername("senki")).isEmpty();
        assertThat(repository.findByUsername("Anna")).as("a keresés a normalizált (kisbetűs) nevet várja").isEmpty();
    }

    @Test
    void systemClockReturnsTheCurrentTime() {
        Instant before = Instant.now();
        Instant now = new SystemClockAdapter().now();
        Instant after = Instant.now();

        assertThat(now).isBetween(before, after);
    }
}
