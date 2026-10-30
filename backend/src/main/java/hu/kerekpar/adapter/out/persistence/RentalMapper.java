package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.Rental;

final class RentalMapper {

    private RentalMapper() {
    }

    static Rental toDomain(RentalJpaEntity entity) {
        return new Rental(
                EntityIds.toDomain(EntityIds.RENTAL, entity.getId()),
                EntityIds.toDomain(EntityIds.BIKE, entity.getBikeId()),
                EntityIds.toDomain(EntityIds.USER, entity.getUserId()),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getFee());
    }

    static RentalJpaEntity toNewEntity(Rental rental) {
        return new RentalJpaEntity(
                EntityIds.require(EntityIds.RENTAL, rental.id()),
                EntityIds.require(EntityIds.BIKE, rental.bikeId()),
                EntityIds.require(EntityIds.USER, rental.userId()),
                rental.startedAt(),
                rental.endedAt(),
                rental.fee());
    }

    static void applyTo(Rental rental, RentalJpaEntity entity) {
        entity.update(
                EntityIds.require(EntityIds.BIKE, rental.bikeId()),
                EntityIds.require(EntityIds.USER, rental.userId()),
                rental.startedAt(),
                rental.endedAt(),
                rental.fee());
    }
}
