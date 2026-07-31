package org.evchargingplatform.reservation.domain.model;

import java.util.UUID;

public record ConnectorId(UUID value) {
    public ConnectorId {
        if (value == null)
            throw new IllegalArgumentException("Connector id is required");
    }
}
