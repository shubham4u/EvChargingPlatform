package org.evchargingplatform.payments.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A driver's saved card, held as the provider's token. No card numbers are stored; the brand and last
 * four digits are kept for display only.
 */
public record PaymentMethod(UUID userId, String token, String brand, String last4, Instant updatedAt)
        implements Serializable {
}
