package hu.kerekpar.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import hu.kerekpar.application.port.in.RentalView;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Rental;
import hu.kerekpar.domain.model.Tariff;
import hu.kerekpar.domain.model.User;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A REST API kérés- és válaszmodelljei; az enumok kisbetűs (pl. "in_use") alakban közlekednek. */
public final class ApiDtos {

    private ApiDtos() {
    }

    static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    public record UserResponse(String id, String name, String role) {
        static UserResponse from(User user) {
            return new UserResponse(user.id(), user.name(), wire(user.role()));
        }
    }

    public record TariffResponse(long baseFee, long perMinute) {
        static Map<String, TariffResponse> from(Map<BikeType, Tariff> tariffs) {
            Map<String, TariffResponse> result = new LinkedHashMap<>();
            tariffs.forEach((type, t) -> result.put(wire(type), new TariffResponse(t.baseFee(), t.perMinute())));
            return result;
        }
    }

    public record BikeResponse(String id, String name, String type, String status) {
        static BikeResponse from(Bike bike) {
            return new BikeResponse(bike.id(), bike.name(), wire(bike.type()), wire(bike.status()));
        }
    }

    public record CreateBikeRequest(String name, BikeType type) {
    }

    public record ChangeBikeStatusRequest(BikeStatus status) {
    }

    public record CreateRentalRequest(String bikeId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RentalResponse(String id, String bikeId, String userId, long startedAt, Long endedAt, Long fee,
                                 long elapsedSeconds, long currentFee) {
        static RentalResponse from(RentalView view) {
            Rental r = view.rental();
            return new RentalResponse(
                    r.id(), r.bikeId(), r.userId(),
                    r.startedAt().toEpochMilli(),
                    r.endedAt() == null ? null : r.endedAt().toEpochMilli(),
                    r.fee(),
                    view.elapsedSeconds(), view.currentFee());
        }
    }

    public record ApiError(String code, String message) {
    }
}
