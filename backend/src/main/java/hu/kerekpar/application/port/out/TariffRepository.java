package hu.kerekpar.application.port.out;

import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import java.util.Map;
import java.util.Optional;

public interface TariffRepository {

    Map<BikeType, Tariff> findAll();

    Optional<Tariff> findByType(BikeType type);
}
