package org.evchargingplatform.payments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentJpaRepository extends JpaRepository<PaymentIntentEntity, UUID> {

    Optional<PaymentIntentEntity> findBySessionId(UUID sessionId);

    List<PaymentIntentEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PaymentIntentEntity> findBySessionIdIn(Collection<UUID> sessionIds);
}
