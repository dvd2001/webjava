package hu.kerekpar.application.port.out;

import hu.kerekpar.domain.model.Bike;
import java.util.List;
import java.util.Optional;

public interface BikeRepository {

    List<Bike> findAll();

    Optional<Bike> findById(String id);

    boolean existsByName(String name);

    Bike save(Bike bike);

    String nextId();
}
