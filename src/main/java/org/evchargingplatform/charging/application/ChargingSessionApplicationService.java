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
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.exception.ChargingSessionNotFoundException;
import org.evchargingplatform.charging.domain.exception.ConnectorNotReservedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing all charging session use cases.
 * <p>
 * Decision 1 (reservation validation): when a start command carries a {@code reservationId},
 * the connector must be {@code RESERVED} for that connector and user in the Station Service's
 * {@link ConnectorReservationProjectionRepository}. This is called in-process through the port
 * interface, never the station's JPA classes. That is a deliberate shortcut which only holds while
 * Charging and Station share one deployable. If Charging is split out, it must build its own
 * projection from reservation events instead.
 * <p>
 * Decision 2 (REST shape): the use cases are exposed as {@code POST /charging/sessions} and
 * {@code PATCH /charging/sessions/{id}/stop}, following the resource convention the Reservation
 * API already established rather than the verb-style {@code POST /charging/start|stop} from the
 * original API plan, so the two contexts read the same to clients.
 * <p>
 * There is no separate approval step yet, so {@link #start} moves a session straight through
 * REQUESTED to ACTIVE and publishes both lifecycle events.
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
                .orElseThrow(() -> new ChargingSessionNotFoundException(chargingSessionId));
    }

    @Override
    public ChargingSession start(StartChargingSessionCommand command) {
        if (command.reservationId() != null) {
            ConnectorReservationProjection projection = connectorReservationProjectionRepository
                    .findByReservationId(command.reservationId())
                    .orElseThrow(() -> new ConnectorNotReservedException(
                            "Connector is not reserved for reservation ID: " + command.reservationId()));
            if (!projection.isReserved()
                    || !projection.connectorId().equals(command.connectorId())
                    || !projection.userId().equals(command.userId())) {
                throw new ConnectorNotReservedException(
                        "Connector is not reserved for the provided reservation ID, connector ID, and user ID.");
            }
        }

        ChargingSession requested = ChargingSession.create(
                command.stationId(), command.connectorId(), command.userId(), command.vehicleId(),
                command.reservationId(), clock);
        ChargingSession savedRequested = chargingSessionRepository.save(requested);
        eventPublisher.ifAvailable(publisher -> publisher.sessionRequested(savedRequested));

        ChargingSession active = savedRequested.start(clock);
        ChargingSession savedActive = chargingSessionRepository.save(active);
        eventPublisher.ifAvailable(publisher -> publisher.sessionStarted(savedActive));

        return savedActive;
    }

    @Override
    public ChargingSession stop(UUID sessionId, BigDecimal totalEnergyKwh) {
        ChargingSession session = chargingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChargingSessionNotFoundException(sessionId));
        ChargingSession stoppedSession = session.stop(totalEnergyKwh, clock);
        ChargingSession saved = chargingSessionRepository.save(stoppedSession);
        eventPublisher.ifAvailable(publisher -> publisher.sessionStopped(saved));
        return saved;
    }

}
