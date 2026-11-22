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
import org.evchargingplatform.shared.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link ReservationController}.
 * <p>
 * Uses MockMvc with mocked use case interfaces to test
 * HTTP request/response mapping, error handling, and who may call what.
 */
@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
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
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("POST /reservations")
    class CreateReservation {

        @Test
        @DisplayName("should return 201 Created and reserve as the token's user")
        void shouldReturn201WhenValid() throws Exception {
            Reservation reservation = buildReservation(ReservationStatus.CREATED, USER_ID);
            when(createReservationUseCase.create(any(CreateReservationCommand.class))).thenReturn(reservation);

            mockMvc.perform(post("/reservations").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Instant.now().plus(Duration.ofMinutes(5)))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.stationId").value(STATION_ID.toString()))
                    .andExpect(jsonPath("$.reservationNumber").exists());

            ArgumentCaptor<CreateReservationCommand> captor = ArgumentCaptor.forClass(CreateReservationCommand.class);
            verify(createReservationUseCase).create(captor.capture());
            assertEquals(USER_ID, captor.getValue().userId());
        }

        @Test
        @DisplayName("should return 401 without a token")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(post("/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Instant.now().plus(Duration.ofMinutes(5)))))
                    .andExpect(status().isUnauthorized());
            verify(createReservationUseCase, never()).create(any());
        }

        @Test
        @DisplayName("should return 400 Bad Request when stationId is missing")
        void shouldReturn400WhenStationIdMissing() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "chargerId", CHARGER_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString(),
                    "startTime", Instant.now().plus(Duration.ofMinutes(5)).toString()));

            mockMvc.perform(post("/reservations").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when startTime is in the past")
        void shouldReturn400WhenStartTimeInPast() throws Exception {
            when(createReservationUseCase.create(any(CreateReservationCommand.class)))
                    .thenThrow(new IllegalArgumentException("startTime must be in the future"));

            mockMvc.perform(post("/reservations").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Instant.now().minus(Duration.ofMinutes(5)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }
    }

    @Nested
    @DisplayName("GET /reservations")
    class FindAll {

        @Test
        @DisplayName("should list only the driver's own reservations")
        void shouldListOwnForDriver() throws Exception {
            when(getReservationUseCase.findByUser(USER_ID)).thenReturn(List.of(
                    buildReservation(ReservationStatus.CREATED, USER_ID), buildReservation(ReservationStatus.ACTIVE, USER_ID)));

            mockMvc.perform(get("/reservations").with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
            verify(getReservationUseCase, never()).findAll();
        }

        @Test
        @DisplayName("should list every reservation for an operator")
        void shouldListAllForOperator() throws Exception {
            when(getReservationUseCase.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/reservations").with(operator(UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /reservations/{id}")
    class FindById {

        @Test
        @DisplayName("should return 200 OK for the owner")
        void shouldReturn200WhenFound() throws Exception {
            Reservation reservation = buildReservation(ReservationStatus.CREATED, USER_ID);
            when(getReservationUseCase.findById(reservation.id())).thenReturn(reservation);

            mockMvc.perform(get("/reservations/{id}", reservation.id()).with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservation.id().toString()))
                    .andExpect(jsonPath("$.status").value("CREATED"));
        }

        @Test
        @DisplayName("should return 403 for another driver's reservation")
        void shouldReturn403ForOthers() throws Exception {
            Reservation reservation = buildReservation(ReservationStatus.CREATED, OTHER_USER_ID);
            when(getReservationUseCase.findById(reservation.id())).thenReturn(reservation);

            mockMvc.perform(get("/reservations/{id}", reservation.id()).with(driver(USER_ID)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 Not Found when reservation does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(getReservationUseCase.findById(id)).thenThrow(new ReservationNotFoundException(id));

            mockMvc.perform(get("/reservations/{id}", id).with(driver(USER_ID)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Reservation not found"));
        }
    }

    @Nested
    @DisplayName("PATCH /reservations/{id}/cancel")
    class Cancel {

        @Test
        @DisplayName("should return 200 OK when the owner cancels")
        void shouldReturn200WhenCancelled() throws Exception {
            Reservation created = buildReservation(ReservationStatus.CREATED, USER_ID);
            when(getReservationUseCase.findById(created.id())).thenReturn(created);
            when(cancelReservationUseCase.cancel(created.id())).thenReturn(buildReservation(ReservationStatus.CANCELLED, USER_ID));

            mockMvc.perform(patch("/reservations/{id}/cancel", created.id()).with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("should return 403 and not cancel another driver's reservation")
        void shouldReturn403ForOthers() throws Exception {
            Reservation created = buildReservation(ReservationStatus.CREATED, OTHER_USER_ID);
            when(getReservationUseCase.findById(created.id())).thenReturn(created);

            mockMvc.perform(patch("/reservations/{id}/cancel", created.id()).with(driver(USER_ID)))
                    .andExpect(status().isForbidden());
            verify(cancelReservationUseCase, never()).cancel(any());
        }

        @Test
        @DisplayName("should return 409 Conflict when reservation is already terminal")
        void shouldReturn409WhenAlreadyTerminal() throws Exception {
            Reservation cancelled = buildReservation(ReservationStatus.CANCELLED, USER_ID);
            when(getReservationUseCase.findById(cancelled.id())).thenReturn(cancelled);
            when(cancelReservationUseCase.cancel(cancelled.id())).thenThrow(new IllegalStateException("Reservation is terminal"));

            mockMvc.perform(patch("/reservations/{id}/cancel", cancelled.id()).with(driver(USER_ID)))
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
            Reservation active = buildReservation(ReservationStatus.ACTIVE, USER_ID);
            when(getReservationUseCase.findById(active.id())).thenReturn(active);
            when(completeReservationUseCase.complete(active.id())).thenReturn(buildReservation(ReservationStatus.COMPLETED, USER_ID));

            mockMvc.perform(patch("/reservations/{id}/complete", active.id()).with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("should return 409 Conflict when reservation is not ACTIVE")
        void shouldReturn409WhenNotActive() throws Exception {
            Reservation created = buildReservation(ReservationStatus.CREATED, USER_ID);
            when(getReservationUseCase.findById(created.id())).thenReturn(created);
            when(completeReservationUseCase.complete(created.id()))
                    .thenThrow(new IllegalStateException("Only active reservations can complete"));

            mockMvc.perform(patch("/reservations/{id}/complete", created.id()).with(driver(USER_ID)))
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
            Reservation created = buildReservation(ReservationStatus.CREATED, USER_ID);
            when(getReservationUseCase.findById(created.id())).thenReturn(created);
            when(cancelReservationUseCase.cancel(created.id())).thenReturn(buildReservation(ReservationStatus.CANCELLED, USER_ID));

            mockMvc.perform(delete("/reservations/{id}", created.id()).with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    private String body(Instant startTime) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "stationId", STATION_ID.toString(),
                "chargerId", CHARGER_ID.toString(),
                "vehicleId", VEHICLE_ID.toString(),
                "startTime", startTime.toString()));
    }

    private Reservation buildReservation(ReservationStatus status, UUID userId) {
        Instant now = Instant.now();
        return new Reservation(
                UUID.randomUUID(),
                "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                STATION_ID, CHARGER_ID, userId, VEHICLE_ID,
                now.plus(Duration.ofMinutes(5)),
                now.plus(Duration.ofMinutes(15)),
                status, now, now);
    }
}
