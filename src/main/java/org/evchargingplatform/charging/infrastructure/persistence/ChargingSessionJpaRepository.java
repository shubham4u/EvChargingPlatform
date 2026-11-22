package org.evchargingplatform.charging.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingSessionJpaRepository 
extends JpaRepository<ChargingSessionEntity, UUID> {
        
}
