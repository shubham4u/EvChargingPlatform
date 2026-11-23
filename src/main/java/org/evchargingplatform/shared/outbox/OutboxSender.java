package org.evchargingplatform.shared.outbox;

/**
 * Delivers one outbox event to the broker, blocking until it is acknowledged.
 *
 * @throws Exception if the broker didn't accept it; the relay retries on its next poll
 */
interface OutboxSender {
    void send(String topic, String key, Object event) throws Exception;
}
