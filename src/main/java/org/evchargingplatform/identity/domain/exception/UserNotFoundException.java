package org.evchargingplatform.identity.domain.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID userId) {
        super("No user profile for " + userId + "; the account owner registers one with POST /users/me");
    }
}
