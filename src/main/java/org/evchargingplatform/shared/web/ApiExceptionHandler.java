package org.evchargingplatform.shared.web;

import org.evchargingplatform.charging.domain.exception.ChargingSessionNotFoundException;
import org.evchargingplatform.charging.domain.exception.ConnectorInUseException;
import org.evchargingplatform.charging.domain.exception.ConnectorNotReservedException;
import org.evchargingplatform.charging.domain.exception.VehicleAlreadyChargingException;
import org.evchargingplatform.identity.domain.exception.DuplicateUserException;
import org.evchargingplatform.identity.domain.exception.UserAlreadyRegisteredException;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
import org.evchargingplatform.reservation.domain.exception.ConnectorAlreadyReservedException;
import org.evchargingplatform.reservation.domain.exception.ReservationAlreadyCancelledException;
import org.evchargingplatform.reservation.domain.exception.ReservationAlreadyConfirmedException;
import org.evchargingplatform.reservation.domain.exception.ReservationAlreadyExpiredException;
import org.evchargingplatform.reservation.domain.exception.ReservationNotFoundException;
import org.evchargingplatform.station.domain.exception.ConnectorNotFoundException;
import org.evchargingplatform.station.domain.exception.DuplicateStationException;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.evchargingplatform.vehicle.domain.exception.DuplicateVehicleException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotFoundException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotOwnedByUserException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(ConnectorNotFoundException.class)
    ProblemDetail handleConnectorNotFound(ConnectorNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Connector not found", exception.getMessage());
    }

    // --- Identity exceptions ---

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "User not found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateUserException.class)
    ProblemDetail handleDuplicateUser(DuplicateUserException exception) {
        return problem(HttpStatus.CONFLICT, "User already exists", exception.getMessage());
    }

    @ExceptionHandler(UserAlreadyRegisteredException.class)
    ProblemDetail handleUserAlreadyRegistered(UserAlreadyRegisteredException exception) {
        return problem(HttpStatus.CONFLICT, "User already registered", exception.getMessage());
    }

    // --- Vehicle exceptions ---

    @ExceptionHandler(VehicleNotFoundException.class)
    ProblemDetail handleVehicleNotFound(VehicleNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Vehicle not found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateVehicleException.class)
    ProblemDetail handleDuplicateVehicle(DuplicateVehicleException exception) {
        return problem(HttpStatus.CONFLICT, "Vehicle already registered", exception.getMessage());
    }

    @ExceptionHandler(VehicleNotOwnedByUserException.class)
    ProblemDetail handleVehicleNotOwned(VehicleNotOwnedByUserException exception) {
        return problem(HttpStatus.CONFLICT, "Vehicle not owned by user", exception.getMessage());
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

    // --- Charging session exceptions ---

    @ExceptionHandler(ChargingSessionNotFoundException.class)
    ProblemDetail handleChargingSessionNotFound(ChargingSessionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Charging session not found", exception.getMessage());
    }

    @ExceptionHandler(ConnectorNotReservedException.class)
    ProblemDetail handleConnectorNotReserved(ConnectorNotReservedException exception) {
        return problem(HttpStatus.CONFLICT, "Connector not reserved", exception.getMessage());
    }

    @ExceptionHandler(ConnectorInUseException.class)
    ProblemDetail handleConnectorInUse(ConnectorInUseException exception) {
        return problem(HttpStatus.CONFLICT, "Connector in use", exception.getMessage());
    }

    @ExceptionHandler(VehicleAlreadyChargingException.class)
    ProblemDetail handleVehicleAlreadyCharging(VehicleAlreadyChargingException exception) {
        return problem(HttpStatus.CONFLICT, "Vehicle already charging", exception.getMessage());
    }

    // --- Security ---

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    // --- Generic exceptions ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request contains invalid fields");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Invalid value '" + exception.getValue() + "' for parameter '" + exception.getName() + "'");
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
