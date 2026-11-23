package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.ChargingEventConsumer;
import org.evchargingplatform.station.application.port.out.ConnectorOccupancyRepository;
import org.evchargingplatform.station.domain.ConnectorOccupancy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Maintains the connector occupancy read model from charging session events.
 * <p>
 * Events are keyed by session id, so one session's events arrive in order, but two sessions on the
 * same connector can land on different partitions. The handlers therefore never let an older session
 * overwrite or clear a newer one:
 * <ul>
 *   <li>started: ignored if the connector already shows a different session that started later;</li>
 *   <li>stopped: only clears the connector if it still shows that same session.</li>
 * </ul>
 * Redelivered events produce the same state (idempotent).
 */
@Service
@Transactional
public class OccupancyProjectionService implements ChargingEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OccupancyProjectionService.class);

    private final ConnectorOccupancyRepository occupancyRepository;
    private final Clock clock;

    public OccupancyProjectionService(ConnectorOccupancyRepository occupancyRepository, Clock clock) {
        this.occupancyRepository = occupancyRepository;
        this.clock = clock;
    }

    @Override
    public void onSessionStarted(UUID sessionId, UUID stationId, UUID connectorId, Instant startedAt) {
        Optional<ConnectorOccupancy> current = occupancyRepository.findByConnectorId(connectorId);
        if (current.isPresent() && !current.get().sessionId().equals(sessionId)
                && current.get().startedAt().isAfter(startedAt)) {
            log.warn("Ignoring stale start of session {} on connector {}: newer session {} already shown",
                    sessionId, connectorId, current.get().sessionId());
            return;
        }
        occupancyRepository.save(new ConnectorOccupancy(connectorId, stationId, sessionId, startedAt, Instant.now(clock)));
        log.info("Connector {} at station {} marked OCCUPIED by session {}", connectorId, stationId, sessionId);
    }

    @Override
    public void onSessionStopped(UUID sessionId, UUID stationId, UUID connectorId, Instant stoppedAt) {
        Optional<ConnectorOccupancy> current = occupancyRepository.findByConnectorId(connectorId);
        if (current.isEmpty() || !current.get().sessionId().equals(sessionId)) {
            log.info("Stop of session {} on connector {} needs no change (connector shows {})", sessionId, connectorId,
                    current.map(o -> "session " + o.sessionId()).orElse("no session"));
            return;
        }
        occupancyRepository.deleteByConnectorId(connectorId);
        log.info("Connector {} at station {} is free again (session {} stopped)", connectorId, stationId, sessionId);
    }
}
