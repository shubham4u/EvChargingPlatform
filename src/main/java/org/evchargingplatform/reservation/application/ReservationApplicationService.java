package org.evchargingplatform.reservation.application;

import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.reservation.application.port.in.CancelReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CompleteReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CreateReservationCommand;
import org.evchargingplatform.reservation.application.port.in.CreateReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.ExpireReservationsUseCase;
import org.evchargingplatform.reservation.application.port.in.GetReservationUseCase;
import org.evchargingplatform.reservation.application.port.out.ReservationEventPublisher;
import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.exception.ConnectorAlreadyReservedException;
import org.evchargingplatform.reservation.domain.exception.ReservationNotFoundException;
import org.evchargingplatform.reservation.domain.repository.ReservationRepository;
import org.evchargingplatform.station.application.port.in.GetConnectorsUseCase;
import org.evchargingplatform.vehicle.application.port.in.GetVehicleUseCase;
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
 * <p>
 * Sprint 6: {@link #create} checks the user, that the vehicle is theirs, and that the charger is a
 * connector at the station, through each owning context's query use case in-process. It then
 * refuses a reservation whose time window overlaps an open (CREATED or ACTIVE) one on the same
 * charger.
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
    private final GetUserUseCase getUserUseCase;
    private final GetVehicleUseCase getVehicleUseCase;
    private final GetConnectorsUseCase getConnectorsUseCase;

    public ReservationApplicationService(
            ReservationRepository reservationRepository,
            ObjectProvider<ReservationEventPublisher> eventPublisher,
            Clock clock,
            GetUserUseCase getUserUseCase,
            GetVehicleUseCase getVehicleUseCase,
            GetConnectorsUseCase getConnectorsUseCase) {
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.getUserUseCase = getUserUseCase;
        this.getVehicleUseCase = getVehicleUseCase;
        this.getConnectorsUseCase = getConnectorsUseCase;
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
        getUserUseCase.findById(command.userId());
        getVehicleUseCase.findOwnedBy(command.vehicleId(), command.userId());
        getConnectorsUseCase.findAtStation(command.stationId(), command.chargerId());
        if (reservationRepository.existsOverlapping(command.chargerId(),
                List.of(ReservationStatus.CREATED, ReservationStatus.ACTIVE),
                reservation.startTime(), reservation.expirationTime())) {
            throw new ConnectorAlreadyReservedException();
        }
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