package org.evchargingplatform.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.application.port.in.RegisterUserCommand;
import org.evchargingplatform.identity.application.port.in.RegisterUserUseCase;
import org.evchargingplatform.identity.application.port.out.UserEventPublisher;
import org.evchargingplatform.identity.application.port.out.UserRepository;
import org.evchargingplatform.identity.domain.User;
import org.evchargingplatform.identity.domain.exception.DuplicateUserException;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing the user registration and lookup use cases.
 */
@Service
@Transactional
public class UserApplicationService implements RegisterUserUseCase, GetUserUseCase {

    private final UserRepository userRepository;
    private final ObjectProvider<UserEventPublisher> eventPublisher;
    private final Clock clock;

    public UserApplicationService(UserRepository userRepository,
                                  ObjectProvider<UserEventPublisher> eventPublisher,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public User register(RegisterUserCommand command) {
        String email = User.normaliseEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException(email);
        }
        User saved = userRepository.save(
                User.register(email, command.displayName(), command.phoneNumber(), clock));
        eventPublisher.ifAvailable(publisher -> publisher.userRegistered(saved));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
