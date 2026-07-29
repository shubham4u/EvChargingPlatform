package org.evchargingplatform.domain.reservations;

import org.evchargingplatform.api.reservations.dto.CreateReservationRequest;
import org.evchargingplatform.api.reservations.dto.ReservationResponse;
import org.evchargingplatform.domain.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryReservationService implements ReservationService {
    private final ConcurrentHashMap<UUID, ReservationResponse> reservations = new ConcurrentHashMap<>();

    @Override
    public ReservationResponse create(CreateReservationRequest request) {
        UUID reservationId = UUID.randomUUID();
        ReservationResponse reservation = new ReservationResponse(
                reservationId,
                request.stationId(),
                request.connectorId(),
                request.userId(),
                "ACTIVE",
                request.expiresAt()
        );
        reservations.put(reservationId, reservation);
        return reservation;
    }

    @Override
    public ReservationResponse getById(UUID reservationId) {
        ReservationResponse reservation = reservations.get(reservationId);
        if (reservation == null) {
            throw new NotFoundException("Reservation not found: " + reservationId);
        }
        return reservation;
    }
}

