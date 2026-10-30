package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

class PostgresTariffRepository implements TariffRepository {

    private final SpringDataTariffRepository jpa;

    PostgresTariffRepository(SpringDataTariffRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<BikeType, Tariff> findAll() {
        Map<BikeType, Tariff> result = new EnumMap<>(BikeType.class);
        jpa.findAll().forEach(entity -> result.put(entity.getBikeType(), TariffMapper.toDomain(entity)));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Optional<Tariff> findByType(BikeType type) {
        return jpa.findById(type).map(TariffMapper::toDomain);
    }
}
