package org.evchargingplatform.identity.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import org.evchargingplatform.identity.domain.User;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String phoneNumber,
        String role,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), user.phoneNumber(),
                user.role().name(), user.createdAt());
    }
}
