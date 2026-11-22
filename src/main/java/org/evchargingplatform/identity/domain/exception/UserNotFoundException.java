package org.evchargingplatform.identity.domain.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }
}
