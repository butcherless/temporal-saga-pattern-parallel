package com.bravo.saga.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Entry point for the Temporal Worker hosting the travel-booking saga Workflow and its Activities.
 * Unlike every other service in this platform, it owns no database.
 *
 * <p>Worker registration ({@code spring.temporal.workers.*}) is not yet wired up in
 * {@code application.yml} — it needs real {@code @Component}-annotated Activity implementations
 * to bind to (see the {@code activities} package's interfaces and this module's own pom.xml
 * description), which in turn need flight/hotel/car/payment-service's REST contracts to exist.
 * {@link com.bravo.saga.orchestrator.workflow.TravelBookingSagaWorkflowImpl} itself is complete
 * for the happy path and unit-testable today via {@code temporal-testing}'s mocked Activities.
 */
@Slf4j
@SpringBootApplication
public class OrchestratorTemporalApplication {

    static void main(final String[] args) {
        SpringApplication.run(OrchestratorTemporalApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    void logStartup() {
        log.info("saga-orchestrator-temporal started");
    }
}
