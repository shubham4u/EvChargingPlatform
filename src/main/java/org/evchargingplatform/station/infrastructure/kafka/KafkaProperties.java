package org.evchargingplatform.station.infrastructure.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaProperties(String stationTopic, String chargerTopic, String telemetryTopic,
                              int retries, String acks, boolean idempotence,
                              String compressionType, int batchSize, int lingerMs) { }
