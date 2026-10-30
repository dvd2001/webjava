package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryTariffRepository implements TariffRepository {

    private final Map<BikeType, Tariff> tariffs;

    public InMemoryTariffRepository() {
        EnumMap<BikeType, Tariff> map = new EnumMap<>(BikeType.class);
        map.put(BikeType.CITY, new Tariff(300, 15));
        map.put(BikeType.MOUNTAIN, new Tariff(400, 20));
        map.put(BikeType.ELECTRIC, new Tariff(500, 35));
        this.tariffs = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<BikeType, Tariff> findAll() {
        return tariffs;
    }

    @Override
    public Optional<Tariff> findByType(BikeType type) {
        return Optional.ofNullable(tariffs.get(type));
    }
}
