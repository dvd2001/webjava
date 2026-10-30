package hu.kerekpar.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** A külső kulcsok szándékosan skalár oszlopok (nincs asszociáció, így nincs betöltési és cascade kérdés). */
@Entity
@Table(name = "rental")
class RentalJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "bike_id", nullable = false)
    private Long bikeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "fee")
    private Long fee;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RentalJpaEntity() {
    }

    RentalJpaEntity(Long id, Long bikeId, Long userId, Instant startedAt, Instant endedAt, Long fee) {
        this.id = id;
        this.bikeId = bikeId;
        this.userId = userId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.fee = fee;
    }

    Long getId() {
        return id;
    }

    Long getBikeId() {
        return bikeId;
    }

    Long getUserId() {
        return userId;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getEndedAt() {
        return endedAt;
    }

    Long getFee() {
        return fee;
    }

    void update(Long bikeId, Long userId, Instant startedAt, Instant endedAt, Long fee) {
        this.bikeId = bikeId;
        this.userId = userId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.fee = fee;
    }
}
