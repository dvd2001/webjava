package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.ApiDtos.CreateRentalRequest;
import hu.kerekpar.adapter.in.web.ApiDtos.RentalResponse;
import hu.kerekpar.application.port.in.ListRentalsUseCase;
import hu.kerekpar.application.port.in.RentBikeUseCase;
import hu.kerekpar.application.port.in.ReturnBikeUseCase;
import hu.kerekpar.domain.exception.InvalidInputException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final ListRentalsUseCase listRentals;
    private final RentBikeUseCase rentBike;
    private final ReturnBikeUseCase returnBike;

    public RentalController(ListRentalsUseCase listRentals, RentBikeUseCase rentBike, ReturnBikeUseCase returnBike) {
        this.listRentals = listRentals;
        this.rentBike = rentBike;
        this.returnBike = returnBike;
    }

    @GetMapping
    public List<RentalResponse> list(@RequestHeader(RequestLoggingFilter.USER_HEADER) String actorId,
                                     @RequestParam(defaultValue = "all") String status) {
        return listRentals.execute(new ListRentalsUseCase.Query(actorId, parseFilter(status)))
                .stream().map(RentalResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RentalResponse> rent(@RequestHeader(RequestLoggingFilter.USER_HEADER) String actorId,
                                               @RequestBody CreateRentalRequest request) {
        RentalResponse created = RentalResponse.from(
                rentBike.execute(new RentBikeUseCase.Command(actorId, request.bikeId())));
        return ResponseEntity.created(URI.create("/api/rentals/" + created.id())).body(created);
    }

    @PostMapping("/{id}/return")
    public RentalResponse giveBack(@RequestHeader(RequestLoggingFilter.USER_HEADER) String actorId,
                                   @PathVariable String id) {
        return RentalResponse.from(returnBike.execute(new ReturnBikeUseCase.Command(actorId, id)));
    }

    private static ListRentalsUseCase.Filter parseFilter(String value) {
        try {
            return ListRentalsUseCase.Filter.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("INVALID_FILTER", "A státusz szűrő értéke: all, active vagy finished.");
        }
    }
}
