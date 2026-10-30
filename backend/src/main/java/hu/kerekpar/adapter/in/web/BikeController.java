package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.ApiDtos.BikeResponse;
import hu.kerekpar.adapter.in.web.ApiDtos.ChangeBikeStatusRequest;
import hu.kerekpar.adapter.in.web.ApiDtos.CreateBikeRequest;
import hu.kerekpar.application.port.in.AddBikeUseCase;
import hu.kerekpar.application.port.in.ChangeBikeStatusUseCase;
import hu.kerekpar.application.port.in.ListBikesUseCase;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bikes")
public class BikeController {

    private final ListBikesUseCase listBikes;
    private final AddBikeUseCase addBike;
    private final ChangeBikeStatusUseCase changeBikeStatus;

    public BikeController(ListBikesUseCase listBikes, AddBikeUseCase addBike,
                          ChangeBikeStatusUseCase changeBikeStatus) {
        this.listBikes = listBikes;
        this.addBike = addBike;
        this.changeBikeStatus = changeBikeStatus;
    }

    @GetMapping
    public List<BikeResponse> list() {
        return listBikes.execute().stream().map(BikeResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<BikeResponse> create(@RequestHeader(RequestLoggingFilter.USER_HEADER) String actorId,
                                               @RequestBody CreateBikeRequest request) {
        BikeResponse created = BikeResponse.from(
                addBike.execute(new AddBikeUseCase.Command(actorId, request.name(), request.type())));
        return ResponseEntity.created(URI.create("/api/bikes/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/status")
    public BikeResponse changeStatus(@RequestHeader(RequestLoggingFilter.USER_HEADER) String actorId,
                                     @PathVariable String id,
                                     @RequestBody ChangeBikeStatusRequest request) {
        return BikeResponse.from(
                changeBikeStatus.execute(new ChangeBikeStatusUseCase.Command(actorId, id, request.status())));
    }
}
