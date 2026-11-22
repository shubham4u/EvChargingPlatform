package org.evchargingplatform.identity.domain.exception;

public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String email) {
        super("User already exists with email: " + email);
    }
}
