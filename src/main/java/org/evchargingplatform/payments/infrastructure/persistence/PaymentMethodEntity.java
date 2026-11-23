package org.evchargingplatform.payments.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_methods")
public class PaymentMethodEntity {
    @Id
    @Column(name = "user_id")
    UUID userId;
    @Column(nullable = false)
    String token;
    String brand;
    @Column(length = 4)
    String last4;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    public PaymentMethodEntity() {
    }

    public PaymentMethodEntity(UUID userId, String token, String brand, String last4, Instant updatedAt) {
        this.userId = userId;
        this.token = token;
        this.brand = brand;
        this.last4 = last4;
        this.updatedAt = updatedAt;
    }

    public UUID userId() { return userId; }
    public String token() { return token; }
    public String brand() { return brand; }
    public String last4() { return last4; }
    public Instant updatedAt() { return updatedAt; }
}
