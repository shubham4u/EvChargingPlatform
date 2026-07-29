package org.evchargingplatform.domain.reservations;

import org.evchargingplatform.api.reservations.dto.CreateReservationRequest;
import org.evchargingplatform.api.reservations.dto.ReservationResponse;

import java.util.UUID;

public interface ReservationService {
    ReservationResponse create(CreateReservationRequest request);

    ReservationResponse getById(UUID reservationId);
}

