package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.Bike;
import java.util.List;

public interface ListBikesUseCase {

    List<Bike> execute();
}
