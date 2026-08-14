package com.bravo.saga.car;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/**
 * Entry point for the Car service.
 *
 * <p>Domain/persistence/web layers not implemented yet — its REST contract (reserve/confirm) isn't
 * designed yet either, unlike {@code booking-service}'s (see docs/travel-booking/controller-layer-plan.md).
 *
 * <p>Re-imports {@link DataSourceAutoConfiguration}: Spring Boot backs it off by default once an
 * R2DBC {@code ConnectionFactory} is present, but Flyway still needs it to build its own JDBC
 * connection from {@code spring.flyway.url} at startup.
 */
@Slf4j
@SpringBootApplication
@Import(DataSourceAutoConfiguration.class)
public class CarServiceApplication {

    static void main(final String[] args) {
        SpringApplication.run(CarServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    void logStartup() {
        log.info("car-service started");
    }
}
