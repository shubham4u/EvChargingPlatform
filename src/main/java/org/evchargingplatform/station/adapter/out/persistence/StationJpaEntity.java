package org.evchargingplatform.station.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.evchargingplatform.station.domain.StationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stations")
public class StationJpaEntity {
    @Id
    UUID id;

    @Column(name = "external_reference", nullable = false, unique = true, length = 100)
    String externalReference;

    @Column(nullable = false, length = 200)
    String name;

    @Column(name = "country_code", nullable = false, length = 2)
    String countryCode;

    @Column(nullable = false, precision = 9, scale = 6)
    BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    StationStatus status;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected StationJpaEntity() {
    }

    StationJpaEntity(UUID id, String externalReference, String name, String countryCode,
                     BigDecimal latitude, BigDecimal longitude, StationStatus status, Instant createdAt) {
        this.id = id;
        this.externalReference = externalReference;
        this.name = name;
        this.countryCode = countryCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.createdAt = createdAt;
    }
}
