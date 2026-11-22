package org.evchargingplatform.vehicle.application;

import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
import org.evchargingplatform.support.TestObjectProvider;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleCommand;
import org.evchargingplatform.vehicle.application.port.out.VehicleEventPublisher;
import org.evchargingplatform.vehicle.application.port.out.VehicleRepository;
import org.evchargingplatform.vehicle.domain.Vehicle;
import org.evchargingplatform.vehicle.domain.exception.DuplicateVehicleException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotFoundException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotOwnedByUserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VehicleApplicationService}.
 */
@ExtendWith(MockitoExtension.class)
class VehicleApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String VIN = "5YJ3E1EA7KF317000";

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleEventPublisher eventPublisher;

    @Mock
    private GetUserUseCase getUserUseCase;

    private VehicleApplicationService service;

    @BeforeEach
    void setUp() {
        service = new VehicleApplicationService(vehicleRepository, TestObjectProvider.of(eventPublisher),
                getUserUseCase, CLOCK);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register, persist, and publish a vehicle for an existing owner")
        void shouldRegisterVehicle() {
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Vehicle result = service.register(new RegisterVehicleCommand(OWNER_ID, VIN, "Tesla", "Model 3"));

            assertEquals(OWNER_ID, result.ownerUserId());
            verify(getUserUseCase).findById(OWNER_ID);
            verify(eventPublisher).vehicleRegistered(result);
        }

        @Test
        @DisplayName("should reject an unknown owner")
        void shouldRejectUnknownOwner() {
            when(getUserUseCase.findById(OWNER_ID)).thenThrow(new UserNotFoundException(OWNER_ID));

            assertThrows(UserNotFoundException.class,
                    () -> service.register(new RegisterVehicleCommand(OWNER_ID, VIN, "Tesla", "Model 3")));
            verify(vehicleRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("should reject a VIN that is already registered")
        void shouldRejectDuplicateVin() {
            when(vehicleRepository.existsByVin(VIN)).thenReturn(true);

            assertThrows(DuplicateVehicleException.class,
                    () -> service.register(new RegisterVehicleCommand(OWNER_ID, VIN.toLowerCase(), "Tesla", "Model 3")));
            verify(vehicleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findOwnedBy")
    class FindOwnedBy {

        @Test
        @DisplayName("should return the vehicle for its owner")
        void shouldReturnForOwner() {
            Vehicle vehicle = Vehicle.register(OWNER_ID, VIN, "Tesla", "Model 3", CLOCK);
            when(vehicleRepository.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

            assertEquals(vehicle, service.findOwnedBy(vehicle.id(), OWNER_ID));
        }

        @Test
        @DisplayName("should throw VehicleNotOwnedByUserException for another user")
        void shouldThrowForOtherUser() {
            Vehicle vehicle = Vehicle.register(OWNER_ID, VIN, "Tesla", "Model 3", CLOCK);
            when(vehicleRepository.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

            assertThrows(VehicleNotOwnedByUserException.class,
                    () -> service.findOwnedBy(vehicle.id(), UUID.randomUUID()));
        }

        @Test
        @DisplayName("should throw VehicleNotFoundException for an unknown vehicle")
        void shouldThrowWhenUnknown() {
            UUID id = UUID.randomUUID();
            when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(VehicleNotFoundException.class, () -> service.findOwnedBy(id, OWNER_ID));
        }
    }

    @Test
    @DisplayName("findByOwner should check the owner exists and return their vehicles")
    void shouldFindByOwner() {
        when(vehicleRepository.findByOwnerUserId(OWNER_ID))
                .thenReturn(List.of(Vehicle.register(OWNER_ID, VIN, "Tesla", "Model 3", CLOCK)));

        assertEquals(1, service.findByOwner(OWNER_ID).size());
        verify(getUserUseCase).findById(OWNER_ID);
    }
}
