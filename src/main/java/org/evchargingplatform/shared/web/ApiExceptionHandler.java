package org.evchargingplatform.shared.web;

import org.evchargingplatform.station.domain.exception.DuplicateStationException;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(StationNotFoundException.class)
    ProblemDetail handleNotFound(StationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Station not found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateStationException.class)
    ProblemDetail handleConflict(DuplicateStationException exception) {
        return problem(HttpStatus.CONFLICT, "Station already exists", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request contains invalid fields");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.evchargingplatform.org/errors/" + status.value()));
        return problem;
    }
}
