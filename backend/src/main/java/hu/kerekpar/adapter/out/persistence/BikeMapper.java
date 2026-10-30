package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.Bike;

final class BikeMapper {

    private BikeMapper() {
    }

    static Bike toDomain(BikeJpaEntity entity) {
        return new Bike(
                EntityIds.toDomain(EntityIds.BIKE, entity.getId()),
                entity.getName(),
                entity.getType(),
                entity.getStatus());
    }

    static BikeJpaEntity toNewEntity(Bike bike) {
        return new BikeJpaEntity(
                EntityIds.require(EntityIds.BIKE, bike.id()),
                bike.name(),
                bike.type(),
                bike.status());
    }

    /** Meglévő (kezelt) entity módosítása, hogy a verzió és az azonosító megmaradjon. */
    static void applyTo(Bike bike, BikeJpaEntity entity) {
        entity.update(bike.name(), bike.type(), bike.status());
    }
}
