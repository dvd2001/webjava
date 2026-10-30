package hu.kerekpar.application.port.out;

import hu.kerekpar.domain.model.Rental;
import java.util.List;
import java.util.Optional;

public interface RentalRepository {

    List<Rental> findAll();

    Optional<Rental> findById(String id);

    Rental save(Rental rental);

    String nextId();
}
