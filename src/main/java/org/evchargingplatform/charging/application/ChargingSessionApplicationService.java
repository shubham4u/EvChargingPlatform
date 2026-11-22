package org.evchargingplatform.charging.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionCommand;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StopChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.out.ChargingSessionEventPublisher;
import org.evchargingplatform.charging.application.port.out.ChargingSessionRepository;
import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.station.application.port.in.GetConnectorsUseCase;
import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.evchargingplatform.station.domain.ConnectorReservationProjection.ReservationProjectionStatus;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.exception.ChargingSessionNotFoundException;
import org.evchargingplatform.charging.domain.exception.ConnectorInUseException;
import org.evchargingplatform.charging.domain.exception.ConnectorNotReservedException;
import org.evchargingplatform.charging.domain.exception.VehicleAlreadyChargingException;
import org.evchargingplatform.vehicle.application.port.in.GetVehicleUseCase;
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
 * Sprint 6: before a session starts, the user, the vehicle's ownership and the connector's station
 * are checked through each owning context's query use case (same in-process shortcut as Decision 1).
 * A connector or vehicle can only be in one open session at a time, and a walk-up start is refused
 * while another driver's reservation holds the connector. "In use" is derived from Charging's own
 * sessions rather than a connector status, so no other context needs to be told when a session ends.
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
    private final GetUserUseCase getUserUseCase;
    private final GetVehicleUseCase getVehicleUseCase;
    private final GetConnectorsUseCase getConnectorsUseCase;

    public ChargingSessionApplicationService(ChargingSessionRepository chargingSessionRepository,
                                              ObjectProvider<ChargingSessionEventPublisher> eventPublisher,
                                              Clock clock,
                                              ConnectorReservationProjectionRepository connectorReservationProjectionRepository,
                                              GetUserUseCase getUserUseCase,
                                              GetVehicleUseCase getVehicleUseCase,
                                              GetConnectorsUseCase getConnectorsUseCase) {
        this.chargingSessionRepository = chargingSessionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.connectorReservationProjectionRepository = connectorReservationProjectionRepository;
        this.getUserUseCase = getUserUseCase;
        this.getVehicleUseCase = getVehicleUseCase;
        this.getConnectorsUseCase = getConnectorsUseCase;
    }


    @Override
    public List<ChargingSession> getAllChargingSessions() {
        return chargingSessionRepository.findAll();
    }

    @Override
    public List<ChargingSession> findByUser(UUID userId) {
        return chargingSessionRepository.findByUserId(userId);
    }

    @Override
    public ChargingSession findById(UUID chargingSessionId) {
        return chargingSessionRepository.findById(chargingSessionId)
                .orElseThrow(() -> new ChargingSessionNotFoundException(chargingSessionId));
    }

    @Override
    public ChargingSession start(StartChargingSessionCommand command) {
        getUserUseCase.findById(command.userId());
        getVehicleUseCase.findOwnedBy(command.vehicleId(), command.userId());
        getConnectorsUseCase.findAtStation(command.stationId(), command.connectorId());

        if (chargingSessionRepository.existsOpenSessionForConnector(command.connectorId())) {
            throw new ConnectorInUseException("Connector already has an active charging session: " + command.connectorId());
        }
        if (chargingSessionRepository.existsOpenSessionForVehicle(command.vehicleId())) {
            throw new VehicleAlreadyChargingException(command.vehicleId());
        }

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
        } else {
            ensureNotHeldByAnotherReservation(command);
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

    // Ignores holds whose expiry has passed, so a delayed expiry event cannot block the connector.
    private void ensureNotHeldByAnotherReservation(StartChargingSessionCommand command) {
        Instant now = Instant.now(clock);
        boolean held = connectorReservationProjectionRepository
                .findByStationIdAndStatus(command.stationId(), ReservationProjectionStatus.RESERVED).stream()
                .anyMatch(p -> p.connectorId().equals(command.connectorId())
                        && (p.expiresAt() == null || p.expiresAt().isAfter(now)));
        if (held) {
            throw new ConnectorInUseException("Connector is held by a reservation: " + command.connectorId());
        }
    }

}
