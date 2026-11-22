package org.evchargingplatform.identity.domain.exception;

import java.util.UUID;

public class UserAlreadyRegisteredException extends RuntimeException {
    public UserAlreadyRegisteredException(UUID userId) {
        super("A profile is already registered for this account: " + userId);
    }
}
