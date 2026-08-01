package org.evchargingplatform.shared.web;

import org.evchargingplatform.reservation.domain.exception.ConnectorAlreadyReservedException;
import org.evchargingplatform.reservation.domain.exception.ReservationAlreadyCancelledException;
import org.evchargingplatform.reservation.domain.exception.ReservationAlreadyConfirmedException;
import org.evchargingplatform.reservation.domain.exception.ReservationAlreadyExpiredException;
import org.evchargingplatform.reservation.domain.exception.ReservationNotFoundException;
import org.evchargingplatform.station.domain.exception.DuplicateStationException;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    // --- Station exceptions ---

    @ExceptionHandler(StationNotFoundException.class)
    ProblemDetail handleStationNotFound(StationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Station not found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateStationException.class)
    ProblemDetail handleDuplicateStation(DuplicateStationException exception) {
        return problem(HttpStatus.CONFLICT, "Station already exists", exception.getMessage());
    }

    // --- Reservation exceptions ---

    @ExceptionHandler(ReservationNotFoundException.class)
    ProblemDetail handleReservationNotFound(ReservationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Reservation not found", exception.getMessage());
    }

    @ExceptionHandler(ReservationAlreadyCancelledException.class)
    ProblemDetail handleReservationAlreadyCancelled(ReservationAlreadyCancelledException exception) {
        return problem(HttpStatus.CONFLICT, "Reservation already cancelled", exception.getMessage());
    }

    @ExceptionHandler(ReservationAlreadyConfirmedException.class)
    ProblemDetail handleReservationAlreadyConfirmed(ReservationAlreadyConfirmedException exception) {
        return problem(HttpStatus.CONFLICT, "Reservation already confirmed", exception.getMessage());
    }

    @ExceptionHandler(ReservationAlreadyExpiredException.class)
    ProblemDetail handleReservationAlreadyExpired(ReservationAlreadyExpiredException exception) {
        return problem(HttpStatus.CONFLICT, "Reservation already expired", exception.getMessage());
    }

    @ExceptionHandler(ConnectorAlreadyReservedException.class)
    ProblemDetail handleConnectorAlreadyReserved(ConnectorAlreadyReservedException exception) {
        return problem(HttpStatus.CONFLICT, "Connector already reserved", exception.getMessage());
    }

    // --- Generic exceptions ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request contains invalid fields");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleMissing(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, "Invalid state transition", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.evchargingplatform.org/errors/" + status.value()));
        return problem;
    }
}
