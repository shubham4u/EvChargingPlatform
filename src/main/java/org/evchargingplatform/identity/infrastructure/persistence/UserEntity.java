package org.evchargingplatform.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    UUID id;

    @Column(name = "email", nullable = false, unique = true)
    String email;
    String displayName;
    String phoneNumber;
    String role;
    Instant createdAt;

    public UserEntity() {
    }

    public UserEntity(UUID id, String email, String displayName, String phoneNumber, String role,
            Instant createdAt) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
