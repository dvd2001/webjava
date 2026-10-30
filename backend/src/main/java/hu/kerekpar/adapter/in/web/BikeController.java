package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.api.BikesApi;
import hu.kerekpar.adapter.in.web.api.model.BikeDto;
import hu.kerekpar.adapter.in.web.api.model.ChangeBikeStatusRequestDto;
import hu.kerekpar.adapter.in.web.api.model.CreateBikeRequestDto;
import hu.kerekpar.adapter.in.web.security.CurrentActor;
import hu.kerekpar.application.port.in.AddBikeUseCase;
import hu.kerekpar.application.port.in.ChangeBikeStatusUseCase;
import hu.kerekpar.application.port.in.ListBikesUseCase;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BikeController implements BikesApi {

    private final ListBikesUseCase listBikes;
    private final AddBikeUseCase addBike;
    private final ChangeBikeStatusUseCase changeBikeStatus;
    private final CurrentActor actor;

    public BikeController(ListBikesUseCase listBikes, AddBikeUseCase addBike,
                          ChangeBikeStatusUseCase changeBikeStatus, CurrentActor actor) {
        this.listBikes = listBikes;
        this.addBike = addBike;
        this.changeBikeStatus = changeBikeStatus;
        this.actor = actor;
    }

    @Override
    public ResponseEntity<List<BikeDto>> listBikes() {
        return ResponseEntity.ok(listBikes.execute().stream().map(ApiMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<BikeDto> createBike(CreateBikeRequestDto request) {
        BikeDto created = ApiMapper.toDto(addBike.execute(new AddBikeUseCase.Command(
                actor.id(), request.getName(), ApiMapper.toDomain(request.getType()))));
        return ResponseEntity.created(URI.create("/api/bikes/" + created.getId())).body(created);
    }

    @Override
    public ResponseEntity<BikeDto> changeBikeStatus(String id, ChangeBikeStatusRequestDto request) {
        return ResponseEntity.ok(ApiMapper.toDto(changeBikeStatus.execute(new ChangeBikeStatusUseCase.Command(
                actor.id(), id, ApiMapper.toDomain(request.getStatus())))));
    }
}
