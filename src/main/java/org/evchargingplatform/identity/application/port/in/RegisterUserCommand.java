package org.evchargingplatform.identity.application.port.in;

public record RegisterUserCommand(
        String email,
        String displayName,
        String phoneNumber // optional
) {
}
