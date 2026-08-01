package org.evchargingplatform.reservation.application;

import org.evchargingplatform.reservation.application.port.in.CancelReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CompleteReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CreateReservationCommand;
import org.evchargingplatform.reservation.application.port.in.CreateReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.ExpireReservationsUseCase;
import org.evchargingplatform.reservation.application.port.in.GetReservationUseCase;
import org.evchargingplatform.reservation.application.port.out.ReservationEventPublisher;
import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.exception.ReservationNotFoundException;
import org.evchargingplatform.reservation.domain.repository.ReservationRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service implementing all reservation use cases.
 * <p>
 * This is the orchestration layer: it coordinates domain logic, persistence,
 * and event publishing without containing business rules itself.
 */
@Service
@Transactional
public class ReservationApplicationService implements
        CreateReservationUseCase,
        CancelReservationUseCase,
        CompleteReservationUseCase,
        GetReservationUseCase,
        ExpireReservationsUseCase {

    private final ReservationRepository reservationRepository;
    private final ObjectProvider<ReservationEventPublisher> eventPublisher;
    private final Clock clock;

    public ReservationApplicationService(
            ReservationRepository reservationRepository,
            ObjectProvider<ReservationEventPublisher> eventPublisher,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Reservation create(CreateReservationCommand command) {
        Reservation reservation = Reservation.create(
                command.stationId(),
                command.chargerId(),
                command.userId(),
                command.vehicleId(),
                command.startTime(),
                clock);
        Reservation saved = reservationRepository.save(reservation);
        eventPublisher.ifAvailable(publisher -> publisher.reservationCreated(saved));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "reservations", key = "#reservationId")
    public Reservation cancel(UUID reservationId) {
        Reservation reservation = findById(reservationId);
        Reservation cancelled = reservation.cancel(clock);
        Reservation saved = reservationRepository.save(cancelled);
        eventPublisher.ifAvailable(publisher -> publisher.reservationCancelled(saved));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "reservations", key = "#reservationId")
    public Reservation complete(UUID reservationId) {
        Reservation reservation = findById(reservationId);
        Reservation completed = reservation.complete(clock);
        Reservation saved = reservationRepository.save(completed);
        eventPublisher.ifAvailable(publisher -> publisher.reservationCompleted(saved));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findAll() {
        return reservationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reservations", key = "#reservationId")
    public Reservation findById(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    @Override
    @Scheduled(fixedDelayString = "${reservation.expiration.poll-ms:60000}")
    public void expireReservations() {
        Instant now = Instant.now(clock);
        List<Reservation> expired = reservationRepository.findExpired(
                List.of(ReservationStatus.CREATED, ReservationStatus.ACTIVE), now);
        for (Reservation reservation : expired) {
            Reservation expiredReservation = reservation.expire(clock);
            Reservation saved = reservationRepository.save(expiredReservation);
            eventPublisher.ifAvailable(publisher -> publisher.reservationExpired(saved));
        }
    }
}