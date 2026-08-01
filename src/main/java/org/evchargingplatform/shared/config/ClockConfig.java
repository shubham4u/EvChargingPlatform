package org.evchargingplatform.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a shared {@link Clock} bean for time-dependent operations.
 * <p>
 * Using a single Clock bean makes time-based logic testable:
 * tests can inject a fixed clock instead of relying on the system clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}