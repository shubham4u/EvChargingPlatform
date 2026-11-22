package org.evchargingplatform.identity.application.port.in;

import java.util.UUID;

public record RegisterUserCommand(
        UUID userId,     // the caller's token subject
        String email,
        String displayName,
        String phoneNumber // optional
) {
}
