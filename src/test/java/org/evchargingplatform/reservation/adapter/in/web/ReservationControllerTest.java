package org.evchargingplatform.reservation.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.reservation.application.port.in.CancelReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CompleteReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CreateReservationCommand;
import org.evchargingplatform.reservation.application.port.in.CreateReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.GetReservationUseCase;
import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.exception.ReservationNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link ReservationController}.
 * <p>
 * Uses MockMvc with mocked use case interfaces to test
 * HTTP request/response mapping and error handling.
 */
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateReservationUseCase createReservationUseCase;

    @MockBean
    private CancelReservationUseCase cancelReservationUseCase;

    @MockBean
    private CompleteReservationUseCase completeReservationUseCase;

    @MockBean
    private GetReservationUseCase getReservationUseCase;

    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CHARGER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("POST /reservations")
    class CreateReservation {

        @Test
        @DisplayName("should return 201 Created when request is valid")
        void shouldReturn201WhenValid() throws Exception {
            Reservation reservation = buildReservation(ReservationStatus.CREATED);
            when(createReservationUseCase.create(any(CreateReservationCommand.class))).thenReturn(reservation);

            String body = objectMapper.writeValueAsString(Map.of(
                    "stationId", STATION_ID.toString(),
                    "chargerId", CHARGER_ID.toString(),
                    "userId", USER_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString(),
                    "startTime", Instant.now().plus(Duration.ofMinutes(5)).toString()));

            mockMvc.perform(post("/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.stationId").value(STATION_ID.toString()))
                    .andExpect(jsonPath("$.reservationNumber").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when stationId is missing")
        void shouldReturn400WhenStationIdMissing() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "chargerId", CHARGER_ID.toString(),
                    "userId", USER_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString(),
                    "startTime", Instant.now().plus(Duration.ofMinutes(5)).toString()));

            mockMvc.perform(post("/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when startTime is in the past")
        void shouldReturn400WhenStartTimeInPast() throws Exception {
            when(createReservationUseCase.create(any(CreateReservationCommand.class)))
                    .thenThrow(new IllegalArgumentException("startTime must be in the future"));

            String body = objectMapper.writeValueAsString(Map.of(
                    "stationId", STATION_ID.toString(),
                    "chargerId", CHARGER_ID.toString(),
                    "userId", USER_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString(),
                    "startTime", Instant.now().minus(Duration.ofMinutes(5)).toString()));

            mockMvc.perform(post("/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }
    }

    @Nested
    @DisplayName("GET /reservations")
    class FindAll {

        @Test
        @DisplayName("should return 200 OK with list of reservations")
        void shouldReturn200WithList() throws Exception {
            Reservation r1 = buildReservation(ReservationStatus.CREATED);
            Reservation r2 = buildReservation(ReservationStatus.ACTIVE);
            when(getReservationUseCase.findAll()).thenReturn(List.of(r1, r2));

            mockMvc.perform(get("/reservations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("should return 200 OK with empty list")
        void shouldReturn200WithEmptyList() throws Exception {
            when(getReservationUseCase.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/reservations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /reservations/{id}")
    class FindById {

        @Test
        @DisplayName("should return 200 OK when reservation exists")
        void shouldReturn200WhenFound() throws Exception {
            Reservation reservation = buildReservation(ReservationStatus.CREATED);
            when(getReservationUseCase.findById(reservation.id())).thenReturn(reservation);

            mockMvc.perform(get("/reservations/{id}", reservation.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservation.id().toString()))
                    .andExpect(jsonPath("$.status").value("CREATED"));
        }

        @Test
        @DisplayName("should return 404 Not Found when reservation does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(getReservationUseCase.findById(id)).thenThrow(new ReservationNotFoundException(id));

            mockMvc.perform(get("/reservations/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Reservation not found"));
        }
    }

    @Nested
    @DisplayName("PATCH /reservations/{id}/cancel")
    class Cancel {

        @Test
        @DisplayName("should return 200 OK when cancellation succeeds")
        void shouldReturn200WhenCancelled() throws Exception {
            Reservation cancelled = buildReservation(ReservationStatus.CANCELLED);
            when(cancelReservationUseCase.cancel(cancelled.id())).thenReturn(cancelled);

            mockMvc.perform(patch("/reservations/{id}/cancel", cancelled.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("should return 409 Conflict when reservation is already terminal")
        void shouldReturn409WhenAlreadyTerminal() throws Exception {
            UUID id = UUID.randomUUID();
            when(cancelReservationUseCase.cancel(id)).thenThrow(new IllegalStateException("Reservation is terminal"));

            mockMvc.perform(patch("/reservations/{id}/cancel", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Invalid state transition"));
        }
    }

    @Nested
    @DisplayName("PATCH /reservations/{id}/complete")
    class Complete {

        @Test
        @DisplayName("should return 200 OK when completion succeeds")
        void shouldReturn200WhenCompleted() throws Exception {
            Reservation completed = buildReservation(ReservationStatus.COMPLETED);
            when(completeReservationUseCase.complete(completed.id())).thenReturn(completed);

            mockMvc.perform(patch("/reservations/{id}/complete", completed.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("should return 409 Conflict when reservation is not ACTIVE")
        void shouldReturn409WhenNotActive() throws Exception {
            UUID id = UUID.randomUUID();
            when(completeReservationUseCase.complete(id))
                    .thenThrow(new IllegalStateException("Only active reservations can complete"));

            mockMvc.perform(patch("/reservations/{id}/complete", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Only active reservations can complete"));
        }
    }

    @Nested
    @DisplayName("DELETE /reservations/{id}")
    class DeleteCancel {

        @Test
        @DisplayName("should return 200 OK when cancellation via DELETE succeeds")
        void shouldReturn200WhenCancelledViaDelete() throws Exception {
            Reservation cancelled = buildReservation(ReservationStatus.CANCELLED);
            when(cancelReservationUseCase.cancel(cancelled.id())).thenReturn(cancelled);

            mockMvc.perform(delete("/reservations/{id}", cancelled.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    private Reservation buildReservation(ReservationStatus status) {
        Instant now = Instant.now();
        return new Reservation(
                UUID.randomUUID(),
                "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID,
                now.plus(Duration.ofMinutes(5)),
                now.plus(Duration.ofMinutes(15)),
                status, now, now);
    }
}