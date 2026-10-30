package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.BikeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Csak olvasott referenciaadat (a V2 migráció tölti fel). */
@Entity
@Table(name = "tariff")
class TariffJpaEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "bike_type")
    private BikeType bikeType;

    @Column(name = "base_fee", nullable = false)
    private long baseFee;

    @Column(name = "per_minute", nullable = false)
    private long perMinute;

    protected TariffJpaEntity() {
    }

    BikeType getBikeType() {
        return bikeType;
    }

    long getBaseFee() {
        return baseFee;
    }

    long getPerMinute() {
        return perMinute;
    }
}
