package com.bravo.saga.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Binds the {@code services.*.base-url} block from {@code application.yml} — one typed bean
 * instead of five independent {@code @Value("${services.*.base-url}")} placeholders, so a
 * missing or malformed entry fails at binding time against the full property path, not as five
 * unrelated string lookups each failing (or silently defaulting) on their own.
 */
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(Service flight,
        Service hotel,
        Service car,
        Service payment,
        Service booking) {

    public ServicesProperties {
        Objects.requireNonNull(flight, "flight must not be null");
        Objects.requireNonNull(hotel, "hotel must not be null");
        Objects.requireNonNull(car, "car must not be null");
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(booking, "booking must not be null");
    }

    public record Service(String baseUrl) {

        public Service {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        }
    }
}
