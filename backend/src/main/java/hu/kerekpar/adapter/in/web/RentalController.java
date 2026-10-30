package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.api.RentalsApi;
import hu.kerekpar.adapter.in.web.api.model.CreateRentalRequestDto;
import hu.kerekpar.adapter.in.web.api.model.RentalDto;
import hu.kerekpar.application.port.in.ListRentalsUseCase;
import hu.kerekpar.application.port.in.RentBikeUseCase;
import hu.kerekpar.application.port.in.ReturnBikeUseCase;
import hu.kerekpar.domain.exception.InvalidInputException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RentalController implements RentalsApi {

    private final ListRentalsUseCase listRentals;
    private final RentBikeUseCase rentBike;
    private final ReturnBikeUseCase returnBike;

    public RentalController(ListRentalsUseCase listRentals, RentBikeUseCase rentBike, ReturnBikeUseCase returnBike) {
        this.listRentals = listRentals;
        this.rentBike = rentBike;
        this.returnBike = returnBike;
    }

    @Override
    public ResponseEntity<List<RentalDto>> listRentals(String actorId, String status) {
        List<RentalDto> rentals = listRentals.execute(new ListRentalsUseCase.Query(actorId, parseFilter(status)))
                .stream().map(ApiMapper::toDto).toList();
        return ResponseEntity.ok(rentals);
    }

    @Override
    public ResponseEntity<RentalDto> createRental(String actorId, CreateRentalRequestDto request) {
        RentalDto created = ApiMapper.toDto(rentBike.execute(new RentBikeUseCase.Command(actorId, request.getBikeId())));
        return ResponseEntity.created(URI.create("/api/rentals/" + created.getId())).body(created);
    }

    @Override
    public ResponseEntity<RentalDto> returnRental(String id, String actorId) {
        return ResponseEntity.ok(ApiMapper.toDto(returnBike.execute(new ReturnBikeUseCase.Command(actorId, id))));
    }

    private static ListRentalsUseCase.Filter parseFilter(String value) {
        try {
            return ListRentalsUseCase.Filter.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("INVALID_FILTER", "A státusz szűrő értéke: all, active vagy finished.");
        }
    }
}
