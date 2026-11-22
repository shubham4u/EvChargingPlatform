package org.evchargingplatform.charging.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "charging_sessions")
public class ChargingSessionEntity {
    @Id
    UUID id;

    @Column(name = "charging_session_number", nullable = false, unique = true) 
    String chargingSessionNumber;
    UUID stationId;
    UUID connectorId;
    UUID userId;

    UUID vehicleId;
    UUID reservationId;
    String status;
    Instant startedAt;
    Instant stoppedAt;
    BigDecimal totalEnergyKwh;
    Instant createdAt;
    Instant updatedAt;
    
    @Version
    long version;

    public ChargingSessionEntity() {
    }

    public ChargingSessionEntity(UUID id, String chargingSessionNumber, UUID stationId, UUID connectorId,
            UUID userId, UUID vehicleId, UUID reservationId, String status, Instant startedAt, Instant stoppedAt,
            BigDecimal totalEnergyKwh, Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.chargingSessionNumber = chargingSessionNumber;
        this.stationId = stationId;
        this.connectorId = connectorId;
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.reservationId = reservationId;
        this.status = status;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
        this.totalEnergyKwh = totalEnergyKwh;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID id() {
        return id;
    }

    public String chargingSessionNumber() {
        return chargingSessionNumber;
    }   

    public UUID stationId() {
        return stationId;
    }

    public UUID connectorId() {
        return connectorId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public UUID reservationId() {
        return reservationId;
    }

    public String status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant stoppedAt() {
        return stoppedAt;
    }

    public BigDecimal totalEnergyKwh() {
        return totalEnergyKwh;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
    
}
