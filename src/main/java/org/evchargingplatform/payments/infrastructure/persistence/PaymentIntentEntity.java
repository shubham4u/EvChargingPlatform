package org.evchargingplatform.payments.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
public class PaymentIntentEntity {
    @Id
    UUID id;
    @Column(name = "session_id", nullable = false, unique = true)
    UUID sessionId;
    @Column(name = "user_id", nullable = false)
    UUID userId;
    @Column(nullable = false, length = 20)
    String status;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "authorized_amount", nullable = false, precision = 12, scale = 2)
    BigDecimal authorizedAmount;
    @Column(name = "captured_amount", precision = 12, scale = 2)
    BigDecimal capturedAmount;
    @Column(name = "card_token", nullable = false)
    String cardToken;
    @Column(name = "card_brand")
    String cardBrand;
    @Column(name = "card_last4", length = 4)
    String cardLast4;
    @Column(name = "provider_reference")
    String providerReference;
    @Column(name = "failure_code")
    String failureCode;
    @Column(name = "failure_reason")
    String failureReason;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    @Version
    long version;

    public PaymentIntentEntity() {
    }

    public PaymentIntentEntity(UUID id, UUID sessionId, UUID userId, String status, String currency,
            BigDecimal authorizedAmount, BigDecimal capturedAmount, String cardToken, String cardBrand, String cardLast4,
            String providerReference, String failureCode, String failureReason, Instant createdAt, Instant updatedAt,
            long version) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = status;
        this.currency = currency;
        this.authorizedAmount = authorizedAmount;
        this.capturedAmount = capturedAmount;
        this.cardToken = cardToken;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.providerReference = providerReference;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID id() { return id; }
    public UUID sessionId() { return sessionId; }
    public UUID userId() { return userId; }
    public String status() { return status; }
    public String currency() { return currency; }
    public BigDecimal authorizedAmount() { return authorizedAmount; }
    public BigDecimal capturedAmount() { return capturedAmount; }
    public String cardToken() { return cardToken; }
    public String cardBrand() { return cardBrand; }
    public String cardLast4() { return cardLast4; }
    public String providerReference() { return providerReference; }
    public String failureCode() { return failureCode; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
