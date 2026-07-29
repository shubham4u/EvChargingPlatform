package org.evchargingplatform.domain.charging;

import org.evchargingplatform.api.charging.dto.ChargingSessionResponse;
import org.evchargingplatform.api.charging.dto.StartChargingRequest;
import org.evchargingplatform.api.charging.dto.StopChargingRequest;
import org.evchargingplatform.domain.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryChargingService implements ChargingService {
    private final ConcurrentHashMap<UUID, ChargingSessionResponse> sessions = new ConcurrentHashMap<>();

    @Override
    public ChargingSessionResponse start(StartChargingRequest request) {
        UUID sessionId = UUID.randomUUID();
        ChargingSessionResponse session = new ChargingSessionResponse(
                sessionId,
                request.stationId(),
                request.connectorId(),
                request.userId(),
                "ACTIVE",
                Instant.now(),
                null,
                null
        );
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public ChargingSessionResponse stop(StopChargingRequest request) {
        ChargingSessionResponse existing = sessions.get(request.sessionId());
        if (existing == null) {
            throw new NotFoundException("Charging session not found: " + request.sessionId());
        }

        ChargingSessionResponse stopped = new ChargingSessionResponse(
                existing.sessionId(),
                existing.stationId(),
                existing.connectorId(),
                existing.userId(),
                "STOPPED",
                existing.startedAt(),
                Instant.now(),
                request.meterReadingKwh() == null ? BigDecimal.ZERO : request.meterReadingKwh()
        );
        sessions.put(existing.sessionId(), stopped);
        return stopped;
    }

    @Override
    public ChargingSessionResponse getById(UUID sessionId) {
        ChargingSessionResponse session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Charging session not found: " + sessionId);
        }
        return session;
    }
}

