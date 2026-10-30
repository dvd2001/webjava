package hu.kerekpar.domain.model;

import java.util.Objects;

public record User(String id, String name, UserRole role) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
    }

    public boolean isManager() {
        return role == UserRole.MANAGER;
    }

    public boolean isRenter() {
        return role == UserRole.RENTER;
    }
}
