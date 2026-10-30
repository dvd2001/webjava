package hu.kerekpar.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import hu.kerekpar.adapter.in.web.api.model.BikeStatusDto;
import hu.kerekpar.adapter.in.web.api.model.BikeTypeDto;
import hu.kerekpar.adapter.in.web.api.model.LoginResponseDto;
import hu.kerekpar.adapter.in.web.api.model.RentalDto;
import hu.kerekpar.adapter.in.web.api.model.TariffDto;
import hu.kerekpar.adapter.in.web.api.model.UserRoleDto;
import hu.kerekpar.application.port.in.LoginUseCase;
import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiMapperTest {

    @Test
    void everyBikeTypeRoundTripsAndKeepsTheContractValue() {
        for (BikeType type : BikeType.values()) {
            BikeTypeDto dto = ApiMapper.toDto(type);

            assertThat(dto.getValue()).isEqualTo(type.name().toLowerCase());
            assertThat(ApiMapper.toDomain(dto)).isEqualTo(type);
        }
    }

    @Test
    void everyBikeStatusRoundTripsAndKeepsTheContractValue() {
        for (BikeStatus status : BikeStatus.values()) {
            BikeStatusDto dto = ApiMapper.toDto(status);

            assertThat(dto.getValue()).isEqualTo(status.name().toLowerCase());
            assertThat(ApiMapper.toDomain(dto)).isEqualTo(status);
        }
        assertThat(ApiMapper.toDto(BikeStatus.IN_USE).getValue()).isEqualTo("in_use");
    }

    @Test
    void missingEnumValuesStayNullSoTheDomainCanRejectThem() {
        assertThat(ApiMapper.toDomain((BikeTypeDto) null)).isNull();
        assertThat(ApiMapper.toDomain((BikeStatusDto) null)).isNull();
    }

    @Test
    void userRoleIsMappedToTheContractValue() {
        assertThat(ApiMapper.toDto(UserRole.MANAGER)).isEqualTo(UserRoleDto.MANAGER);
        assertThat(ApiMapper.toDto(UserRole.RENTER).getValue()).isEqualTo("renter");
        assertThat(ApiMapper.toDto(new User("u1", "Kovács Anna", UserRole.MANAGER)).getName()).isEqualTo("Kovács Anna");
    }

    @Test
    void bikeMapsAllFields() {
        var dto = ApiMapper.toDto(new Bike("b3", "City-03", BikeType.CITY, BikeStatus.MAINTENANCE));

        assertThat(dto.getId()).isEqualTo("b3");
        assertThat(dto.getName()).isEqualTo("City-03");
        assertThat(dto.getType()).isEqualTo(BikeTypeDto.CITY);
        assertThat(dto.getStatus()).isEqualTo(BikeStatusDto.MAINTENANCE);
    }

    @Test
    void tariffsAreKeyedByTheContractTypeValue() {
        Map<BikeType, Tariff> tariffs = new EnumMap<>(BikeType.class);
        tariffs.put(BikeType.ELECTRIC, new Tariff(500, 35));
        tariffs.put(BikeType.CITY, new Tariff(300, 15));

        Map<String, TariffDto> dto = ApiMapper.toDto(tariffs);

        assertThat(dto).containsOnlyKeys("city", "electric");
        assertThat(dto.get("electric").getBaseFee()).isEqualTo(500L);
        assertThat(dto.get("electric").getPerMinute()).isEqualTo(35L);
    }

    @Test
    void activeRentalOmitsEndAndFee() {
        Instant start = Instant.parse("2026-03-14T09:30:00Z");

        RentalDto dto = ApiMapper.toDto(new RentalView(Rental.start("r1", "b1", "u2", start), 90, 345));

        assertThat(dto.getStartedAt()).isEqualTo(start.toEpochMilli());
        assertThat(dto.getEndedAt()).isNull();
        assertThat(dto.getFee()).isNull();
        assertThat(dto.getElapsedSeconds()).isEqualTo(90L);
        assertThat(dto.getCurrentFee()).isEqualTo(345L);
    }

    @Test
    void finishedRentalCarriesEndAndFee() {
        Instant start = Instant.parse("2026-03-14T09:30:00Z");
        Rental finished = Rental.start("r1", "b1", "u2", start).finish(start.plusSeconds(600), 450);

        RentalDto dto = ApiMapper.toDto(new RentalView(finished, 600, 450));

        assertThat(dto.getEndedAt()).isEqualTo(start.plusSeconds(600).toEpochMilli());
        assertThat(dto.getFee()).isEqualTo(450L);
    }

    @Test
    void loginResultBecomesABearerResponseWithTheUser() {
        Instant expiry = Instant.parse("2026-03-14T10:00:00Z");

        LoginResponseDto dto = ApiMapper.toDto(new LoginUseCase.LoginResult(
                new User("u2", "Nagy Péter", UserRole.RENTER), "jwt-token", expiry));

        assertThat(dto.getAccessToken()).isEqualTo("jwt-token");
        assertThat(dto.getTokenType()).isEqualTo(LoginResponseDto.TokenTypeEnum.BEARER);
        assertThat(dto.getExpiresAt()).isEqualTo(expiry.toEpochMilli());
        assertThat(dto.getUser().getId()).isEqualTo("u2");
        assertThat(dto.getUser().getRole()).isEqualTo(UserRoleDto.RENTER);
    }
}
