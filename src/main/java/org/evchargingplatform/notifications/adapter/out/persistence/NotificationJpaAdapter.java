package org.evchargingplatform.notifications.adapter.out.persistence;

import org.evchargingplatform.notifications.application.port.out.NotificationRepository;
import org.evchargingplatform.notifications.domain.Notification;
import org.evchargingplatform.notifications.domain.NotificationType;
import org.evchargingplatform.notifications.infrastructure.persistence.NotificationEntity;
import org.evchargingplatform.notifications.infrastructure.persistence.NotificationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter for the notification inbox.
 */
@Component
public class NotificationJpaAdapter implements NotificationRepository {

    private final NotificationJpaRepository repository;

    public NotificationJpaAdapter(NotificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Notification save(Notification n) {
        return toDomain(repository.save(new NotificationEntity(n.id(), n.userId(), n.eventId(), n.type().name(), n.title(),
                n.body(), n.subjectId(), n.createdAt(), n.readAt())));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    public List<Notification> findByUserId(UUID userId, boolean unreadOnly) {
        List<NotificationEntity> rows = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId)
                : repository.findByUserIdOrderByCreatedAtDesc(userId);
        return rows.stream().map(this::toDomain).toList();
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(e.id(), e.userId(), e.eventId(), NotificationType.valueOf(e.type()), e.title(), e.body(),
                e.subjectId(), e.createdAt(), e.readAt());
    }
}
