package org.evchargingplatform.reservation.domain.repository;
import org.evchargingplatform.reservation.domain.*; import java.time.Instant; import java.util.*;
public interface ReservationRepository { Reservation save(Reservation reservation); Optional<Reservation> findById(UUID id); List<Reservation> findAll(); List<Reservation> findExpired(Collection<ReservationStatus> statuses, Instant at); }
