package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.domain.model.Bike;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryBikeRepository implements BikeRepository {

    private final Map<String, Bike> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized List<Bike> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public synchronized Optional<Bike> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public synchronized boolean existsByName(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return store.values().stream().anyMatch(b -> b.name().toLowerCase(Locale.ROOT).equals(wanted));
    }

    @Override
    public synchronized Bike save(Bike bike) {
        store.put(bike.id(), bike);
        return bike;
    }

    @Override
    public synchronized String nextId() {
        String id;
        do {
            id = "b" + sequence.incrementAndGet();
        } while (store.containsKey(id));
        return id;
    }
}
