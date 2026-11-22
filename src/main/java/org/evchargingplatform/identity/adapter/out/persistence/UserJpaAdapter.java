package org.evchargingplatform.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.identity.application.port.out.UserRepository;
import org.evchargingplatform.identity.domain.User;
import org.evchargingplatform.identity.domain.UserRole;
import org.evchargingplatform.identity.infrastructure.persistence.UserEntity;
import org.evchargingplatform.identity.infrastructure.persistence.UserJpaRepository;
import org.springframework.stereotype.Component;

/**
 * JPA persistence adapter for the user repository port.
 */
@Component
public class UserJpaAdapter implements UserRepository {

    private final UserJpaRepository repository;

    public UserJpaAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        return toDomain(repository.save(toEntity(user)));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<User> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    private UserEntity toEntity(User u) {
        return new UserEntity(u.id(), u.email(), u.displayName(), u.phoneNumber(), u.role().name(), u.createdAt());
    }

    private User toDomain(UserEntity e) {
        return new User(e.id(), e.email(), e.displayName(), e.phoneNumber(), UserRole.valueOf(e.role()),
                e.createdAt());
    }
}
