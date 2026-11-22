package org.evchargingplatform.vehicle.domain;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Vehicle aggregate: an EV registered to exactly one owning user.
 * <p>
 * The owner is held as a raw {@code UUID} (same choice as Sprint 5 Decision 3). Charging and
 * reservations use {@link #isOwnedBy} so a driver can only charge or reserve for their own car.
 */
public record Vehicle(UUID id, UUID ownerUserId, String vin, String make, String model, Instant createdAt)
        implements Serializable {

    // ISO 3779: 17 characters, letters I, O and Q are never used
    private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    public static Vehicle register(UUID ownerUserId, String vin, String make, String model, Clock clock) {
        String normalisedVin = normaliseVin(vin);
        if (!VIN.matcher(normalisedVin).matches()) {
            throw new IllegalArgumentException("VIN must be 17 characters (A-Z except I, O, Q, and 0-9)");
        }
        return new Vehicle(UUID.randomUUID(), ownerUserId, normalisedVin, make.trim(), model.trim(),
                Instant.now(clock));
    }

    public static String normaliseVin(String vin) {
        return vin.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId.equals(userId);
    }
}
