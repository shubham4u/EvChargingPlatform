package org.evchargingplatform.station.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.evchargingplatform.station.domain.ConnectorType;

public record AddConnectorRequest(
        @NotNull ConnectorType connectorType,
        @NotNull @Positive @Max(1000) Integer maxPowerKw) {
}
