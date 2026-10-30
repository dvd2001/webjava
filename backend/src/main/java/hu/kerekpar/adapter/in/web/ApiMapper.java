package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.api.model.BikeDto;
import hu.kerekpar.adapter.in.web.api.model.BikeSearchResultDto;
import hu.kerekpar.adapter.in.web.api.model.BikeStatusDto;
import hu.kerekpar.adapter.in.web.api.model.BikeTypeDto;
import hu.kerekpar.adapter.in.web.api.model.LoginResponseDto;
import hu.kerekpar.adapter.in.web.api.model.RentalDto;
import hu.kerekpar.adapter.in.web.api.model.TariffDto;
import hu.kerekpar.adapter.in.web.api.model.UserDto;
import hu.kerekpar.adapter.in.web.api.model.UserRoleDto;
import hu.kerekpar.application.port.in.LoginUseCase;
import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.application.port.in.SearchBikesUseCase;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import java.util.LinkedHashMap;
import java.util.Map;

/** A domain és az OpenAPI szerződésből generált DTO-k közötti explicit leképezés. */
final class ApiMapper {

    private ApiMapper() {
    }

    static UserDto toDto(User user) {
        return new UserDto(user.id(), user.name(), toDto(user.role()));
    }

    static LoginResponseDto toDto(LoginUseCase.LoginResult result) {
        return new LoginResponseDto(result.accessToken(), LoginResponseDto.TokenTypeEnum.BEARER,
                result.expiresAt().toEpochMilli(), toDto(result.user()));
    }

    static BikeSearchResultDto toDto(SearchBikesUseCase.SearchResult result) {
        return new BikeSearchResultDto(result.items().stream().map(ApiMapper::toDto).toList(), result.total());
    }

    /** A keresés típus-paramétere (a szerződés kisbetűs értéke); az üres érték "nincs szűrés". */
    static BikeType parseBikeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (BikeTypeDto candidate : BikeTypeDto.values()) {
            if (candidate.getValue().equals(raw.trim())) {
                return toDomain(candidate);
            }
        }
        throw new InvalidInputException("INVALID_BIKE_TYPE", "A típus értéke: city, mountain vagy electric.");
    }

    /** A keresés státusz-paramétere (a szerződés kisbetűs értéke); az üres érték "nincs szűrés". */
    static BikeStatus parseBikeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (BikeStatusDto candidate : BikeStatusDto.values()) {
            if (candidate.getValue().equals(raw.trim())) {
                return toDomain(candidate);
            }
        }
        throw new InvalidInputException("INVALID_STATUS", "A státusz értéke: available, in_use vagy maintenance.");
    }

    static BikeDto toDto(Bike bike) {
        return new BikeDto(bike.id(), bike.name(), toDto(bike.type()), toDto(bike.status()));
    }

    static TariffDto toDto(Tariff tariff) {
        return new TariffDto(tariff.baseFee(), tariff.perMinute());
    }

    static Map<String, TariffDto> toDto(Map<BikeType, Tariff> tariffs) {
        Map<String, TariffDto> result = new LinkedHashMap<>();
        tariffs.forEach((type, tariff) -> result.put(toDto(type).getValue(), toDto(tariff)));
        return result;
    }

    static RentalDto toDto(RentalView view) {
        Rental rental = view.rental();
        RentalDto dto = new RentalDto(rental.id(), rental.bikeId(), rental.userId(),
                rental.startedAt().toEpochMilli(), view.elapsedSeconds(), view.currentFee());
        if (rental.endedAt() != null) {
            dto.endedAt(rental.endedAt().toEpochMilli());
        }
        dto.fee(rental.fee());
        return dto;
    }

    static UserRoleDto toDto(UserRole role) {
        return switch (role) {
            case MANAGER -> UserRoleDto.MANAGER;
            case RENTER -> UserRoleDto.RENTER;
        };
    }

    static BikeTypeDto toDto(BikeType type) {
        return switch (type) {
            case CITY -> BikeTypeDto.CITY;
            case MOUNTAIN -> BikeTypeDto.MOUNTAIN;
            case ELECTRIC -> BikeTypeDto.ELECTRIC;
        };
    }

    static BikeStatusDto toDto(BikeStatus status) {
        return switch (status) {
            case AVAILABLE -> BikeStatusDto.AVAILABLE;
            case IN_USE -> BikeStatusDto.IN_USE;
            case MAINTENANCE -> BikeStatusDto.MAINTENANCE;
        };
    }

    /** A hiányzó (null) értéket a domain szabályai utasítják el megfelelő hibakóddal. */
    static BikeType toDomain(BikeTypeDto type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case CITY -> BikeType.CITY;
            case MOUNTAIN -> BikeType.MOUNTAIN;
            case ELECTRIC -> BikeType.ELECTRIC;
        };
    }

    static BikeStatus toDomain(BikeStatusDto status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case AVAILABLE -> BikeStatus.AVAILABLE;
            case IN_USE -> BikeStatus.IN_USE;
            case MAINTENANCE -> BikeStatus.MAINTENANCE;
        };
    }
}
