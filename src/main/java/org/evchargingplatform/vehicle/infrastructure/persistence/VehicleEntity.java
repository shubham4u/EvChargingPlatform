package org.evchargingplatform.vehicle.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {
    @Id
    UUID id;

    UUID ownerUserId;
    @Column(name = "vin", nullable = false, unique = true, length = 17)
    String vin;
    String make;
    String model;
    Instant createdAt;

    public VehicleEntity() {
    }

    public VehicleEntity(UUID id, UUID ownerUserId, String vin, String make, String model, Instant createdAt) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.vin = vin;
        this.make = make;
        this.model = model;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerUserId() {
        return ownerUserId;
    }

    public String vin() {
        return vin;
    }

    public String make() {
        return make;
    }

    public String model() {
        return model;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
