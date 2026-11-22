package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.Connector;

public interface AddConnectorUseCase {
    Connector addConnector(AddConnectorCommand command);
}
