package org.evchargingplatform.payments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, UUID> {
}
