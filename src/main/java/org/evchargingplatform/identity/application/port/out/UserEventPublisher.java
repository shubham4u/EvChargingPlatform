package org.evchargingplatform.identity.application.port.out;

import org.evchargingplatform.identity.domain.User;

public interface UserEventPublisher {

    void userRegistered(User user);
}
