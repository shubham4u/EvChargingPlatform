package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorAvailability;

public record ConnectorStatus(Connector connector, ConnectorAvailability availability) {
}
