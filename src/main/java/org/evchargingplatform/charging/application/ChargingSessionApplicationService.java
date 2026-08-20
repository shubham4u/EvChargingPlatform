package org.evchargingplatform.charging.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionCommand;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StopChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.out.ChargingSessionEventPublisher;
import org.evchargingplatform.charging.application.port.out.ChargingSessionRepository;
import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.exception.ChargingSessionNotFoundException;
import org.evchargingplatform.charging.domain.exception.ConnectorNotReservedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for managing charging sessions.
 * This service implements the use cases for starting, stopping, and retrieving charging sessions.
 */

@Service
@Transactional
public class ChargingSessionApplicationService implements 
StartChargingSessionUseCase, StopChargingSessionUseCase, GetChargingSessionUseCase {

    private final ChargingSessionRepository chargingSessionRepository;
    private final ObjectProvider<ChargingSessionEventPublisher> eventPublisher;
    private final Clock clock;
    private final ConnectorReservationProjectionRepository connectorReservationProjectionRepository;

    public ChargingSessionApplicationService(ChargingSessionRepository chargingSessionRepository,
                                              ObjectProvider<ChargingSessionEventPublisher> eventPublisher,
                                              Clock clock,
                                              ConnectorReservationProjectionRepository connectorReservationProjectionRepository) {
        this.chargingSessionRepository = chargingSessionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.connectorReservationProjectionRepository = connectorReservationProjectionRepository;
    }


    @Override
    public List<ChargingSession> getAllChargingSessions() {
        return chargingSessionRepository.findAll();
    }

    @Override
    public ChargingSession findById(UUID chargingSessionId) {
        return chargingSessionRepository.findById(chargingSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Charging session not found: " + chargingSessionId));
    }

    @Override
    public ChargingSession start(StartChargingSessionCommand command) {
        if (command.reservationId() != null) {
            connectorReservationProjectionRepository.findByReservationId(command.reservationId())
                    .orElseThrow(() -> new ConnectorNotReservedException("Connector is not reserved for reservation ID: " + command.reservationId()));
        }
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public ChargingSession stop(UUID sessionId, BigDecimal totalEnergyKwh) {
        ChargingSession session = chargingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChargingSessionNotFoundException(sessionId));
        ChargingSession stoppedSession = session.stop(totalEnergyKwh, clock);
        chargingSessionRepository.save(stoppedSession);
        eventPublisher.ifAvailable(publisher -> publisher.sessionStopped(stoppedSession));
        return stoppedSession;
    }

}
