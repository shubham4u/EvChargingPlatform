package org.evchargingplatform.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tariffs")
public class TariffEntity {
    @Id
    UUID id;

    @Column(name = "station_id", nullable = false)
    UUID stationId;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "price_per_kwh", nullable = false, precision = 10, scale = 4)
    BigDecimal pricePerKwh;
    @Column(name = "price_per_minute", nullable = false, precision = 10, scale = 4)
    BigDecimal pricePerMinute;
    @Column(name = "session_fee", nullable = false, precision = 10, scale = 2)
    BigDecimal sessionFee;
    @Column(name = "defined_by")
    UUID definedBy;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    public TariffEntity() {
    }

    public TariffEntity(UUID id, UUID stationId, String currency, BigDecimal pricePerKwh, BigDecimal pricePerMinute,
            BigDecimal sessionFee, UUID definedBy, Instant createdAt) {
        this.id = id;
        this.stationId = stationId;
        this.currency = currency;
        this.pricePerKwh = pricePerKwh;
        this.pricePerMinute = pricePerMinute;
        this.sessionFee = sessionFee;
        this.definedBy = definedBy;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID stationId() {
        return stationId;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal pricePerKwh() {
        return pricePerKwh;
    }

    public BigDecimal pricePerMinute() {
        return pricePerMinute;
    }

    public BigDecimal sessionFee() {
        return sessionFee;
    }

    public UUID definedBy() {
        return definedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
