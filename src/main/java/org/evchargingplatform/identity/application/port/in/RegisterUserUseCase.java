package org.evchargingplatform.identity.application.port.in;

import org.evchargingplatform.identity.domain.User;

public interface RegisterUserUseCase {
    User register(RegisterUserCommand command);
}
