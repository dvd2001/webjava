package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "bike")
class BikeJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BikeType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BikeStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected BikeJpaEntity() {
    }

    BikeJpaEntity(Long id, String name, BikeType type, BikeStatus status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    BikeType getType() {
        return type;
    }

    BikeStatus getStatus() {
        return status;
    }

    void update(String name, BikeType type, BikeStatus status) {
        this.name = name;
        this.type = type;
        this.status = status;
    }
}
