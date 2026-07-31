package org.evchargingplatform.station.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaTopicConfiguration {
    @Bean NewTopic stationEventsTopic(KafkaProperties p) { return new NewTopic(p.stationTopic(), 3, (short) 1); }
    @Bean NewTopic chargerEventsTopic(KafkaProperties p) { return new NewTopic(p.chargerTopic(), 3, (short) 1); }
    @Bean NewTopic telemetryEventsTopic(KafkaProperties p) { return new NewTopic(p.telemetryTopic(), 3, (short) 1); }
}
