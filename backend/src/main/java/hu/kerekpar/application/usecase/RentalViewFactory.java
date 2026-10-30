package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import java.time.Duration;
import java.time.Instant;

class RentalViewFactory {

    private final BikeRepository bikes;
    private final TariffRepository tariffs;

    RentalViewFactory(BikeRepository bikes, TariffRepository tariffs) {
        this.bikes = bikes;
        this.tariffs = tariffs;
    }

    RentalView create(Rental rental, Instant now) {
        if (!rental.isActive()) {
            long elapsed = Duration.between(rental.startedAt(), rental.endedAt()).toSeconds();
            return new RentalView(rental, Math.max(0, elapsed), rental.fee());
        }
        Bike bike = bikes.findById(rental.bikeId())
                .orElseThrow(() -> new NotFoundException("BIKE_NOT_FOUND", "A bicikli nem található."));
        Tariff tariff = tariffs.findByType(bike.type())
                .orElseThrow(() -> new NotFoundException("TARIFF_NOT_FOUND", "A tarifa nem található."));
        long elapsed = Duration.between(rental.startedAt(), now).toSeconds();
        return new RentalView(rental, Math.max(0, elapsed), tariff.feeFor(rental.startedAt(), now));
    }
}
