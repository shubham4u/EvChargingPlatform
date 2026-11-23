package org.evchargingplatform.charging.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.charging.application.port.out.ChargingSessionRepository;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.ChargingSessionStatus;
import org.evchargingplatform.charging.domain.exception.ConnectorInUseException;
import org.evchargingplatform.charging.domain.exception.VehicleAlreadyChargingException;
import org.evchargingplatform.charging.infrastructure.persistence.ChargingSessionEntity;
import org.evchargingplatform.charging.infrastructure.persistence.ChargingSessionJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ChargingSessionJpaAdapter implements ChargingSessionRepository {
    
    private static final List<String> OPEN_STATUSES = List.of(
            ChargingSessionStatus.REQUESTED.name(), ChargingSessionStatus.ACTIVE.name());

    private final ChargingSessionJpaRepository repository;

    public ChargingSessionJpaAdapter(ChargingSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ChargingSession> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<ChargingSession> findByUserId(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ChargingSession> findByStation(UUID stationId, Instant from, Instant to) {
        return repository.findByStationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(stationId, from, to)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ChargingSession> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public ChargingSession save(ChargingSession chargingSession) {
        // Flush now so a lost race on the open-session unique indexes (V14) surfaces here as the usual 409,
        // not as a generic error at commit.
        try {
            return toDomain(repository.saveAndFlush(toEntity(chargingSession)));
        } catch (DataIntegrityViolationException e) {
            String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("ux_charging_sessions_open_connector")) {
                throw new ConnectorInUseException("Connector already has an active charging session: " + chargingSession.connectorId());
            }
            if (detail.contains("ux_charging_sessions_open_vehicle")) {
                throw new VehicleAlreadyChargingException(chargingSession.vehicleId());
            }
            throw e;
        }
    }

    // The domain record carries no version, so reuse the persisted one. Rebuilding the entity
    // with version 0 makes Hibernate reject every update after the first as a stale write.
    private long currentVersion(UUID id) {
        return repository.findById(id).map(ChargingSessionEntity::version).orElse(0L);
    }

    private ChargingSession toDomain(ChargingSessionEntity save) {
        return new ChargingSession(
                save.id(),
                save.chargingSessionNumber(),
                save.stationId(),
                save.connectorId(),
                save.userId(),
                save.vehicleId(),
                save.reservationId(),
                save.status() != null ? ChargingSessionStatus.valueOf(save.status()) : null,
                save.startedAt(),
                save.stoppedAt(),
                save.totalEnergyKwh(),
                save.tariffId(),
                save.totalCost(),
                save.currency(),
                save.createdAt(),
                save.updatedAt()
        );
    }

    @Override
    public boolean existsOpenSessionForConnector(UUID connectorId) {
        return repository.existsByConnectorIdAndStatusIn(connectorId, OPEN_STATUSES);
    }

    @Override
    public boolean existsOpenSessionForVehicle(UUID vehicleId) {
        return repository.existsByVehicleIdAndStatusIn(vehicleId, OPEN_STATUSES);
    }

    @Override
    public void delete(ChargingSession chargingSession) {
        repository.delete(toEntity(chargingSession));
    }

    private ChargingSessionEntity toEntity(ChargingSession chargingSession) {
        return new ChargingSessionEntity(
                chargingSession.id(),
                chargingSession.chargingSessionNumber(),
                chargingSession.stationId(),
                chargingSession.connectorId(),
                chargingSession.userId(),
                chargingSession.vehicleId(),
                chargingSession.reservationId(),
                chargingSession.status().name(),
                chargingSession.startedAt(),
                chargingSession.stoppedAt(),
                chargingSession.totalEnergyKwh(),
                chargingSession.tariffId(),
                chargingSession.totalCost(),
                chargingSession.currency(),
                chargingSession.createdAt(),
                chargingSession.updatedAt(),
                currentVersion(chargingSession.id())
        );
    }
    
}
