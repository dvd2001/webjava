package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.ClockPort;
import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Példaadatok betöltése indításkor (megegyezik a frontend példaadataival). */
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final BikeRepository bikes;
    private final RentalRepository rentals;
    private final TariffRepository tariffs;
    private final ClockPort clock;

    public DemoDataSeeder(BikeRepository bikes, RentalRepository rentals, TariffRepository tariffs, ClockPort clock) {
        this.bikes = bikes;
        this.rentals = rentals;
        this.tariffs = tariffs;
        this.clock = clock;
    }

    public void seed() {
        Instant now = clock.now();

        bikes.save(new Bike("b1", "City-01", BikeType.CITY, BikeStatus.AVAILABLE));
        bikes.save(new Bike("b2", "City-02", BikeType.CITY, BikeStatus.IN_USE));
        bikes.save(new Bike("b3", "Hegyi-01", BikeType.MOUNTAIN, BikeStatus.AVAILABLE));
        bikes.save(new Bike("b4", "Hegyi-02", BikeType.MOUNTAIN, BikeStatus.MAINTENANCE));
        bikes.save(new Bike("b5", "E-bike-01", BikeType.ELECTRIC, BikeStatus.AVAILABLE));
        bikes.save(new Bike("b6", "E-bike-02", BikeType.ELECTRIC, BikeStatus.IN_USE));

        Instant finishedStart = now.minus(Duration.ofHours(24));
        Instant finishedEnd = finishedStart.plus(Duration.ofMinutes(40));
        Tariff city = tariffs.findByType(BikeType.CITY).orElseThrow();
        rentals.save(new Rental("r0", "b1", "u4", finishedStart, finishedEnd, city.feeFor(finishedStart, finishedEnd)));
        rentals.save(Rental.start("r1", "b2", "u2", now.minus(Duration.ofMinutes(25))));
        rentals.save(Rental.start("r2", "b6", "u3", now.minus(Duration.ofMinutes(70))));

        log.info("Példaadatok betöltve: {} bicikli, {} bérlés", bikes.findAll().size(), rentals.findAll().size());
    }
}
