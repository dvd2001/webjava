package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Csak olvasott referenciaadat (a V2 migráció tölti fel). */
@Entity
@Table(name = "app_user")
class UserJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    protected UserJpaEntity() {
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    UserRole getRole() {
        return role;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }
}
