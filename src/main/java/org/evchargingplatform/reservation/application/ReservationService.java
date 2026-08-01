package org.evchargingplatform.reservation.application;

import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.repository.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ReservationService {
    private final ReservationRepository repo;
    private final Clock clock = Clock.systemUTC();

    public ReservationService(ReservationRepository r) {
        repo = r;
    }

    @Transactional
    public Reservation create(UUID s, UUID c, UUID u, UUID v, Instant start) {
        return save(Reservation.create(s, c, u, v, start, clock));
    }

    @Transactional
    public Reservation cancel(UUID id) {
        return save(get(id).cancel(clock));
    }

    @Transactional
    public Reservation complete(UUID id) {
        return save(get(id).complete(clock));
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Reservation not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Reservation> all() {
        return repo.findAll();
    }

    @Scheduled(fixedDelayString = "${reservation.expiration.poll-ms:60000}")
    @Transactional
    public void expireReservations() {
        repo.findExpired(List.of(ReservationStatus.CREATED, ReservationStatus.ACTIVE), Instant.now(clock))
                .forEach(e -> save(e.expire(clock)));
    }

    private Reservation save(Reservation r) {
        return repo.save(r);
    }
}
