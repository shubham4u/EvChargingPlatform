package org.evchargingplatform.reservation.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.evchargingplatform.reservation.domain.ReservationStatus;

@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id
    UUID id;
    @Column(name = "reservation_number", nullable = false, unique = true)
    String reservationNumber;
    UUID stationId;
    UUID chargerId;
    UUID userId;
    UUID vehicleId;
    Instant startTime;
    Instant expirationTime;
    @Enumerated(EnumType.STRING)
    ReservationStatus status;
    Instant createdAt;
    Instant updatedAt;

    public ReservationEntity() {
    }

    public ReservationEntity(UUID id, String n, UUID s, UUID c, UUID u, UUID v, Instant st, Instant ex,
            ReservationStatus status, Instant ca, Instant ua) {
        this.id = id;
        reservationNumber = n;
        stationId = s;
        chargerId = c;
        userId = u;
        vehicleId = v;
        startTime = st;
        expirationTime = ex;
        this.status = status;
        createdAt = ca;
        updatedAt = ua;
    }

    public UUID getId() {
        return id;
    }

    public String getReservationNumber() {
        return reservationNumber;
    }

    public UUID getStationId() {
        return stationId;
    }

    public UUID getChargerId() {
        return chargerId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
