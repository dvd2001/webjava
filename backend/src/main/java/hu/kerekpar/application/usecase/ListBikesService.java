package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.ListBikesUseCase;
import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.domain.model.Bike;
import java.util.List;

public class ListBikesService implements ListBikesUseCase {

    private final BikeRepository bikes;

    public ListBikesService(BikeRepository bikes) {
        this.bikes = bikes;
    }

    @Override
    public List<Bike> execute() {
        return bikes.findAll();
    }
}
