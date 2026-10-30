package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.Tariff;

final class TariffMapper {

    private TariffMapper() {
    }

    static Tariff toDomain(TariffJpaEntity entity) {
        return new Tariff(entity.getBaseFee(), entity.getPerMinute());
    }
}
