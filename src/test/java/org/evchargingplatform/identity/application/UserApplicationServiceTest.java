package org.evchargingplatform.identity.application;

import org.evchargingplatform.identity.application.port.in.RegisterUserCommand;
import org.evchargingplatform.identity.application.port.out.UserEventPublisher;
import org.evchargingplatform.identity.application.port.out.UserRepository;
import org.evchargingplatform.identity.domain.User;
import org.evchargingplatform.identity.domain.exception.DuplicateUserException;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
import org.evchargingplatform.support.TestObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserApplicationService}.
 */
@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserEventPublisher eventPublisher;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UserApplicationService(userRepository, TestObjectProvider.of(eventPublisher), CLOCK);
    }

    @Test
    @DisplayName("should register, persist, and publish a user")
    void shouldRegisterUser() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.register(new RegisterUserCommand("Alice@Example.com", "Alice", null));

        assertEquals("alice@example.com", result.email());
        verify(eventPublisher).userRegistered(result);
    }

    @Test
    @DisplayName("should reject a duplicate email regardless of case")
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(DuplicateUserException.class,
                () -> service.register(new RegisterUserCommand("ALICE@example.com", "Alice", null)));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("should register without error when no event publisher is available")
    void shouldRegisterWhenPublisherUnavailable() {
        service = new UserApplicationService(userRepository, TestObjectProvider.empty(), CLOCK);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.register(new RegisterUserCommand("bob@example.com", "Bob", null)));
    }

    @Test
    @DisplayName("should throw UserNotFoundException for an unknown id")
    void shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.findById(id));
    }
}
