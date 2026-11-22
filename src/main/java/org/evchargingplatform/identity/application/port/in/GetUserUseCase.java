package org.evchargingplatform.identity.application.port.in;

import java.util.List;
import java.util.UUID;

import org.evchargingplatform.identity.domain.User;

/**
 * Query port for users. Other contexts (vehicle, reservation, charging) call
 * {@link #findById} in-process to check a {@code userId} refers to a real user.
 */
public interface GetUserUseCase {
    List<User> findAll();

    User findById(UUID userId);
}
