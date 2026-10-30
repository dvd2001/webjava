package hu.kerekpar.config;

import hu.kerekpar.adapter.in.web.RequestLoggingFilter;
import hu.kerekpar.adapter.out.memory.DemoDataSeeder;
import hu.kerekpar.adapter.out.memory.SystemClockAdapter;
import hu.kerekpar.application.port.in.AddBikeUseCase;
import hu.kerekpar.application.port.in.ChangeBikeStatusUseCase;
import hu.kerekpar.application.port.in.ListBikesUseCase;
import hu.kerekpar.application.port.in.ListRentalsUseCase;
import hu.kerekpar.application.port.in.ListTariffsUseCase;
import hu.kerekpar.application.port.in.ListUsersUseCase;
import hu.kerekpar.application.port.in.LoginUseCase;
import hu.kerekpar.application.port.in.RentBikeUseCase;
import hu.kerekpar.application.port.in.ReturnBikeUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.CredentialsRepository;
import hu.kerekpar.application.port.out.PasswordHasher;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.application.port.out.TokenIssuer;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.application.usecase.AddBikeService;
import hu.kerekpar.application.usecase.ChangeBikeStatusService;
import hu.kerekpar.application.usecase.ListBikesService;
import hu.kerekpar.application.usecase.ListRentalsService;
import hu.kerekpar.application.usecase.ListTariffsService;
import hu.kerekpar.application.usecase.ListUsersService;
import hu.kerekpar.application.usecase.LoginService;
import hu.kerekpar.application.usecase.RentBikeService;
import hu.kerekpar.application.usecase.ReturnBikeService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Az egyetlen hely, ahol a rétegek összekötődnek (a use case-ek nem ismerik a Springet). */
@Configuration
public class BeanConfig {

    @Bean
    ClockPort clockPort() {
        return new SystemClockAdapter();
    }

    @Bean
    ListUsersUseCase listUsersUseCase(UserRepository users) {
        return new ListUsersService(users);
    }

    @Bean
    LoginUseCase loginUseCase(CredentialsRepository credentials, PasswordHasher hasher, TokenIssuer tokens) {
        return new LoginService(credentials, hasher, tokens);
    }

    @Bean
    ListTariffsUseCase listTariffsUseCase(TariffRepository tariffs) {
        return new ListTariffsService(tariffs);
    }

    @Bean
    ListBikesUseCase listBikesUseCase(BikeRepository bikes) {
        return new ListBikesService(bikes);
    }

    @Bean
    AddBikeUseCase addBikeUseCase(BikeRepository bikes, UserRepository users, UnitOfWork unitOfWork) {
        return new AddBikeService(bikes, users, unitOfWork);
    }

    @Bean
    ChangeBikeStatusUseCase changeBikeStatusUseCase(BikeRepository bikes, UserRepository users,
                                                    UnitOfWork unitOfWork) {
        return new ChangeBikeStatusService(bikes, users, unitOfWork);
    }

    @Bean
    RentBikeUseCase rentBikeUseCase(BikeRepository bikes, RentalRepository rentals, UserRepository users,
                                    TariffRepository tariffs, ClockPort clock, UnitOfWork unitOfWork) {
        return new RentBikeService(bikes, rentals, users, tariffs, clock, unitOfWork);
    }

    @Bean
    ReturnBikeUseCase returnBikeUseCase(BikeRepository bikes, RentalRepository rentals, UserRepository users,
                                        TariffRepository tariffs, ClockPort clock, UnitOfWork unitOfWork) {
        return new ReturnBikeService(bikes, rentals, users, tariffs, clock, unitOfWork);
    }

    @Bean
    ListRentalsUseCase listRentalsUseCase(BikeRepository bikes, RentalRepository rentals, UserRepository users,
                                          TariffRepository tariffs, ClockPort clock) {
        return new ListRentalsService(bikes, rentals, users, tariffs, clock);
    }

    @Bean
    RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    @ConditionalOnProperty(name = "kerekpar.demo-data.enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner demoDataRunner(BikeRepository bikes, RentalRepository rentals, TariffRepository tariffs,
                                     ClockPort clock) {
        DemoDataSeeder seeder = new DemoDataSeeder(bikes, rentals, tariffs, clock);
        return args -> seeder.seed();
    }
}
