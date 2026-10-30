package hu.kerekpar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.adapter.out.memory.DemoDataSeeder;
import hu.kerekpar.adapter.out.memory.InMemoryBikeRepository;
import hu.kerekpar.adapter.out.memory.InMemoryRentalRepository;
import hu.kerekpar.adapter.out.memory.InMemoryTariffRepository;
import hu.kerekpar.adapter.out.memory.InMemoryUserRepository;
import hu.kerekpar.adapter.out.memory.LockingUnitOfWork;
import hu.kerekpar.application.port.in.AddBikeUseCase;
import hu.kerekpar.application.port.in.ChangeBikeStatusUseCase;
import hu.kerekpar.application.port.in.ListRentalsUseCase;
import hu.kerekpar.application.port.in.RentBikeUseCase;
import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.application.port.in.ReturnBikeUseCase;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.usecase.AddBikeService;
import hu.kerekpar.application.usecase.ChangeBikeStatusService;
import hu.kerekpar.application.usecase.ListRentalsService;
import hu.kerekpar.application.usecase.RentBikeService;
import hu.kerekpar.application.usecase.ReturnBikeService;
import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UseCaseTest {

    private static final String MANAGER = "u1";
    private static final String RENTER = "u2";
    private static final String OTHER_RENTER = "u3";

    private Instant now;
    private InMemoryBikeRepository bikes;
    private InMemoryRentalRepository rentals;

    private AddBikeUseCase addBike;
    private ChangeBikeStatusUseCase changeStatus;
    private RentBikeUseCase rentBike;
    private ReturnBikeUseCase returnBike;
    private ListRentalsUseCase listRentals;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-01-01T10:00:00Z");
        ClockPort clock = () -> now;
        bikes = new InMemoryBikeRepository();
        rentals = new InMemoryRentalRepository();
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryTariffRepository tariffs = new InMemoryTariffRepository();
        LockingUnitOfWork unitOfWork = new LockingUnitOfWork();
        new DemoDataSeeder(bikes, rentals, tariffs, clock).seed();

        addBike = new AddBikeService(bikes, users, unitOfWork);
        changeStatus = new ChangeBikeStatusService(bikes, users, unitOfWork);
        rentBike = new RentBikeService(bikes, rentals, users, tariffs, clock, unitOfWork);
        returnBike = new ReturnBikeService(bikes, rentals, users, tariffs, clock, unitOfWork);
        listRentals = new ListRentalsService(bikes, rentals, users, tariffs, clock);
    }

    @Test
    void managerAddsBikeAsAvailable() {
        Bike bike = addBike.execute(new AddBikeUseCase.Command(MANAGER, "City-03", BikeType.CITY));

        assertThat(bike.status()).isEqualTo(BikeStatus.AVAILABLE);
        assertThat(bikes.findById(bike.id())).contains(bike);
    }

    @Test
    void renterCannotAddBikeAndDuplicateNameIsRejected() {
        assertThatThrownBy(() -> addBike.execute(new AddBikeUseCase.Command(RENTER, "City-03", BikeType.CITY)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> addBike.execute(new AddBikeUseCase.Command(MANAGER, "city-01", BikeType.CITY)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void managerChangesStatusButNotOfBikeInUse() {
        Bike updated = changeStatus.execute(new ChangeBikeStatusUseCase.Command(MANAGER, "b1", BikeStatus.MAINTENANCE));

        assertThat(updated.status()).isEqualTo(BikeStatus.MAINTENANCE);
        assertThatThrownBy(() -> changeStatus.execute(
                new ChangeBikeStatusUseCase.Command(MANAGER, "b2", BikeStatus.MAINTENANCE)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> changeStatus.execute(
                new ChangeBikeStatusUseCase.Command(RENTER, "b1", BikeStatus.AVAILABLE)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rentingMarksBikeInUseAndSecondRentalIsRejected() {
        RentalView view = rentBike.execute(new RentBikeUseCase.Command(RENTER, "b3"));

        assertThat(view.rental().isActive()).isTrue();
        assertThat(bikes.findById("b3")).get().extracting(Bike::status).isEqualTo(BikeStatus.IN_USE);
        assertThatThrownBy(() -> rentBike.execute(new RentBikeUseCase.Command(OTHER_RENTER, "b3")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> rentBike.execute(new RentBikeUseCase.Command(RENTER, "b4")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> rentBike.execute(new RentBikeUseCase.Command(MANAGER, "b5")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> rentBike.execute(new RentBikeUseCase.Command(RENTER, "nincs")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnCalculatesFeeFromTariffAndMeasuredTime() {
        RentalView rented = rentBike.execute(new RentBikeUseCase.Command(RENTER, "b5"));
        now = now.plusSeconds(10 * 60 + 5);

        RentalView returned = returnBike.execute(new ReturnBikeUseCase.Command(RENTER, rented.rental().id()));

        // elektromos: 500 + 11 megkezdett perc * 35
        assertThat(returned.rental().fee()).isEqualTo(500 + 11 * 35);
        assertThat(returned.rental().isActive()).isFalse();
        assertThat(bikes.findById("b5")).get().extracting(Bike::status).isEqualTo(BikeStatus.AVAILABLE);
        assertThatThrownBy(() -> returnBike.execute(new ReturnBikeUseCase.Command(RENTER, rented.rental().id())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyOwnerOrManagerCanReturn() {
        assertThatThrownBy(() -> returnBike.execute(new ReturnBikeUseCase.Command(OTHER_RENTER, "r1")))
                .isInstanceOf(ForbiddenException.class);

        RentalView returned = returnBike.execute(new ReturnBikeUseCase.Command(MANAGER, "r1"));

        assertThat(returned.rental().isActive()).isFalse();
    }

    @Test
    void rentalsAreScopedByRole() {
        List<RentalView> managerView = listRentals.execute(new ListRentalsUseCase.Query(MANAGER, null));
        List<RentalView> renterView = listRentals.execute(new ListRentalsUseCase.Query(RENTER, null));
        List<RentalView> active = listRentals.execute(
                new ListRentalsUseCase.Query(MANAGER, ListRentalsUseCase.Filter.ACTIVE));

        assertThat(managerView).hasSize(3);
        assertThat(renterView).extracting(v -> v.rental().id()).containsExactly("r1");
        assertThat(active).extracting(v -> v.rental().id()).containsExactly("r2", "r1");
        assertThat(active.get(1).elapsedSeconds()).isEqualTo(25 * 60);
        // városi: 300 + 25 * 15
        assertThat(active.get(1).currentFee()).isEqualTo(300 + 25 * 15);
    }

    @Test
    void unknownActorIsRejectedAsUnauthenticated() {
        assertThatThrownBy(() -> listRentals.execute(new ListRentalsUseCase.Query("x", null)))
                .isInstanceOfSatisfying(UnauthenticatedException.class,
                        e -> assertThat(e.code()).isEqualTo("UNKNOWN_ACTOR"));
        assertThatThrownBy(() -> rentBike.execute(new RentBikeUseCase.Command("x", "b3")))
                .isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> addBike.execute(new AddBikeUseCase.Command(null, "City-03", BikeType.CITY)))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void finishedAndAllFiltersAreScopedAndOrdered() {
        List<RentalView> finished = listRentals.execute(
                new ListRentalsUseCase.Query(MANAGER, ListRentalsUseCase.Filter.FINISHED));
        List<RentalView> all = listRentals.execute(new ListRentalsUseCase.Query(MANAGER, ListRentalsUseCase.Filter.ALL));
        List<RentalView> renterFinished = listRentals.execute(
                new ListRentalsUseCase.Query(RENTER, ListRentalsUseCase.Filter.FINISHED));

        assertThat(finished).extracting(v -> v.rental().id()).containsExactly("r0");
        assertThat(finished.get(0).currentFee()).isEqualTo(finished.get(0).rental().fee());
        assertThat(finished.get(0).elapsedSeconds()).isEqualTo(40 * 60);
        assertThat(all).extracting(v -> v.rental().id()).containsExactly("r0", "r2", "r1");
        assertThat(renterFinished).isEmpty();
    }

    @Test
    void activeFeeGrowsWithTheClock() {
        long before = listRentals.execute(new ListRentalsUseCase.Query(RENTER, ListRentalsUseCase.Filter.ACTIVE))
                .get(0).currentFee();

        now = now.plusSeconds(30 * 60);
        RentalView later = listRentals.execute(new ListRentalsUseCase.Query(RENTER, ListRentalsUseCase.Filter.ACTIVE))
                .get(0);

        assertThat(later.currentFee()).isEqualTo(before + 30 * 15);
        assertThat(later.elapsedSeconds()).isEqualTo(55 * 60);
    }

    @Test
    void invalidBikeDataIsRejectedByTheUseCase() {
        assertThatThrownBy(() -> addBike.execute(new AddBikeUseCase.Command(MANAGER, " ", BikeType.CITY)))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> addBike.execute(new AddBikeUseCase.Command(MANAGER, "City-03", null)))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> changeStatus.execute(new ChangeBikeStatusUseCase.Command(MANAGER, "b1", null)))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> changeStatus.execute(new ChangeBikeStatusUseCase.Command(MANAGER, "b999", BikeStatus.AVAILABLE)))
                .isInstanceOf(NotFoundException.class);
        assertThat(bikes.findAll()).hasSize(6);
    }

    @Test
    void returningAnUnknownRentalIsNotFound() {
        assertThatThrownBy(() -> returnBike.execute(new ReturnBikeUseCase.Command(MANAGER, "r999")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.code()).isEqualTo("RENTAL_NOT_FOUND"));
    }

    @Test
    void managerCannotRentAndRenterCannotChangeStatus() {
        assertThatThrownBy(() -> rentBike.execute(new RentBikeUseCase.Command(MANAGER, "b3")))
                .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.code()).isEqualTo("RENTER_ONLY"));
        assertThatThrownBy(() -> changeStatus.execute(
                new ChangeBikeStatusUseCase.Command(OTHER_RENTER, "b1", BikeStatus.MAINTENANCE)))
                .isInstanceOf(ForbiddenException.class);
    }
}
