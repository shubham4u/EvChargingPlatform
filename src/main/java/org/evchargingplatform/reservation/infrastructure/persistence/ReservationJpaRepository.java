package org.evchargingplatform.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
import org.evchargingplatform.reservation.domain.ReservationStatus;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {
    List<ReservationEntity> findByStatusInAndExpirationTimeBefore(Collection<ReservationStatus> s, Instant t);
}
