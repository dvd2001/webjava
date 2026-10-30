package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.RentalRepository;
import hu.kerekpar.domain.model.Rental;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRentalRepository implements RentalRepository {

    private final Map<String, Rental> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized List<Rental> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public synchronized Optional<Rental> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public synchronized Rental save(Rental rental) {
        store.put(rental.id(), rental);
        return rental;
    }

    @Override
    public synchronized String nextId() {
        String id;
        do {
            id = "r" + sequence.incrementAndGet();
        } while (store.containsKey(id));
        return id;
    }
}
