package hu.kerekpar.config;

import hu.kerekpar.adapter.out.memory.InMemoryBikeRepository;
import hu.kerekpar.adapter.out.memory.InMemoryRentalRepository;
import hu.kerekpar.adapter.out.memory.InMemoryTariffRepository;
import hu.kerekpar.adapter.out.memory.InMemoryUserRepository;
import hu.kerekpar.adapter.out.memory.LockingUnitOfWork;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Az alapértelmezett (in-memory) tároló; a "postgres" profil a PostgreSQL adaptert használja helyette. */
@Configuration
@Profile("!postgres")
public class InMemoryPersistenceConfig {

    @Bean
    BikeRepository bikeRepository() {
        return new InMemoryBikeRepository();
    }

    @Bean
    RentalRepository rentalRepository() {
        return new InMemoryRentalRepository();
    }

    @Bean
    UserRepository userRepository() {
        return new InMemoryUserRepository();
    }

    @Bean
    TariffRepository tariffRepository() {
        return new InMemoryTariffRepository();
    }

    @Bean
    UnitOfWork unitOfWork() {
        return new LockingUnitOfWork();
    }
}
