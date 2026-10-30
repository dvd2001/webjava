package hu.kerekpar.domain.model;

import hu.kerekpar.domain.exception.ConflictException;
import hu.kerekpar.domain.exception.InvalidInputException;
import java.util.Objects;

public record Bike(String id, String name, BikeType type, BikeStatus status) {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 50;

    public Bike {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
    }

    public static String normalizeName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException("INVALID_BIKE_NAME",
                    "A bicikli neve " + MIN_NAME_LENGTH + " és " + MAX_NAME_LENGTH + " karakter között legyen.");
        }
        return name;
    }

    public static Bike register(String id, String rawName, BikeType type) {
        if (type == null) {
            throw new InvalidInputException("INVALID_BIKE_TYPE", "A bicikli típusa kötelező.");
        }
        return new Bike(id, normalizeName(rawName), type, BikeStatus.AVAILABLE);
    }

    public Bike changeStatus(BikeStatus target) {
        if (target == null) {
            throw new InvalidInputException("INVALID_STATUS", "A státusz kötelező.");
        }
        if (status == BikeStatus.IN_USE) {
            throw new ConflictException("BIKE_IN_USE",
                    "Használatban lévő bicikli állapota csak visszavétellel változtatható.");
        }
        if (target == BikeStatus.IN_USE) {
            throw new ConflictException("STATUS_NOT_SETTABLE",
                    "A \"használatban\" állapot csak bérléssel állítható be.");
        }
        return new Bike(id, name, type, target);
    }

    public Bike rent() {
        if (status != BikeStatus.AVAILABLE) {
            throw new ConflictException("BIKE_NOT_AVAILABLE", "Ez a bicikli jelenleg nem bérelhető.");
        }
        return new Bike(id, name, type, BikeStatus.IN_USE);
    }

    public Bike giveBack() {
        if (status != BikeStatus.IN_USE) {
            throw new ConflictException("BIKE_NOT_IN_USE", "Ez a bicikli nincs kibérelve.");
        }
        return new Bike(id, name, type, BikeStatus.AVAILABLE);
    }
}
