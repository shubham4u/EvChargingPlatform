package org.evchargingplatform.notifications.domain;

public enum NotificationType {
    RESERVATION_CONFIRMED,
    RESERVATION_CANCELLED,
    RESERVATION_EXPIRED,
    CHARGING_STARTED,
    CHARGING_FINISHED,
    PAYMENT_RECEIVED,
    PAYMENT_DECLINED,
    PAYMENT_FAILED,
    REFUND_ISSUED
}
