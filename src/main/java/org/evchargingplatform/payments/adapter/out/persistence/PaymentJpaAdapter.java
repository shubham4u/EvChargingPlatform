package org.evchargingplatform.payments.adapter.out.persistence;

import org.evchargingplatform.payments.application.port.out.PaymentIntentRepository;
import org.evchargingplatform.payments.application.port.out.PaymentMethodRepository;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.payments.domain.PaymentStatus;
import org.evchargingplatform.payments.infrastructure.persistence.PaymentIntentEntity;
import org.evchargingplatform.payments.infrastructure.persistence.PaymentIntentJpaRepository;
import org.evchargingplatform.payments.infrastructure.persistence.PaymentMethodEntity;
import org.evchargingplatform.payments.infrastructure.persistence.PaymentMethodJpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter for payment intents and saved cards.
 */
@Component
public class PaymentJpaAdapter implements PaymentIntentRepository, PaymentMethodRepository {

    private final PaymentIntentJpaRepository intents;
    private final PaymentMethodJpaRepository methods;

    public PaymentJpaAdapter(PaymentIntentJpaRepository intents, PaymentMethodJpaRepository methods) {
        this.intents = intents;
        this.methods = methods;
    }

    @Override
    public PaymentIntent save(PaymentIntent p) {
        // Same as the other adapters: the domain record has no version, so carry the persisted one.
        long version = intents.findById(p.id()).map(PaymentIntentEntity::version).orElse(0L);
        return toDomain(intents.save(new PaymentIntentEntity(p.id(), p.sessionId(), p.userId(), p.status().name(),
                p.currency(), p.authorizedAmount(), p.capturedAmount(), p.cardToken(), p.cardBrand(), p.cardLast4(),
                p.providerReference(), p.failureCode(), p.failureReason(), p.createdAt(), p.updatedAt(), version)));
    }

    @Override
    public Optional<PaymentIntent> findById(UUID id) {
        return intents.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<PaymentIntent> findBySessionId(UUID sessionId) {
        return intents.findBySessionId(sessionId).map(this::toDomain);
    }

    @Override
    public List<PaymentIntent> findByUserId(UUID userId) {
        return intents.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<PaymentIntent> findAll() {
        return intents.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::toDomain).toList();
    }

    @Override
    public PaymentMethod save(PaymentMethod m) {
        return toDomain(methods.save(new PaymentMethodEntity(m.userId(), m.token(), m.brand(), m.last4(), m.updatedAt())));
    }

    @Override
    public Optional<PaymentMethod> findForUser(UUID userId) {
        return methods.findById(userId).map(this::toDomain);
    }

    private PaymentIntent toDomain(PaymentIntentEntity e) {
        return new PaymentIntent(e.id(), e.sessionId(), e.userId(), PaymentStatus.valueOf(e.status()), e.currency(),
                e.authorizedAmount(), e.capturedAmount(), e.cardToken(), e.cardBrand(), e.cardLast4(),
                e.providerReference(), e.failureCode(), e.failureReason(), e.createdAt(), e.updatedAt());
    }

    private PaymentMethod toDomain(PaymentMethodEntity e) {
        return new PaymentMethod(e.userId(), e.token(), e.brand(), e.last4(), e.updatedAt());
    }
}
