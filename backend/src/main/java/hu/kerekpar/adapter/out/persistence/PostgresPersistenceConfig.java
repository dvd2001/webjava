package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.CredentialsRepository;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A PostgreSQL adapter összekötése; csak a "postgres" profilban aktív, a Spring Data típusok nem hagyják el a csomagot. */
@Configuration
@Profile("postgres")
class PostgresPersistenceConfig {

    @Bean
    IdSequence idSequence(EntityManager entityManager) {
        return new IdSequence(entityManager);
    }

    @Bean
    BikeRepository bikeRepository(SpringDataBikeRepository jpa, IdSequence ids) {
        return new PostgresBikeRepository(jpa, ids);
    }

    @Bean
    RentalRepository rentalRepository(SpringDataRentalRepository jpa, IdSequence ids) {
        return new PostgresRentalRepository(jpa, ids);
    }

    @Bean
    UserRepository userRepository(SpringDataUserRepository jpa) {
        return new PostgresUserRepository(jpa);
    }

    @Bean
    CredentialsRepository credentialsRepository(SpringDataUserRepository jpa) {
        return new PostgresCredentialsRepository(jpa);
    }

    @Bean
    TariffRepository tariffRepository(SpringDataTariffRepository jpa) {
        return new PostgresTariffRepository(jpa);
    }

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new PostgresUnitOfWork(new TransactionTemplate(transactionManager));
    }
}
