package org.evchargingplatform.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.identity.domain.User;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    List<User> findAll();

    boolean existsByEmail(String email);
}
