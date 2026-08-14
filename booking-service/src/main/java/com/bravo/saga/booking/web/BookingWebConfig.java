package com.bravo.saga.booking.web;

import com.bravo.saga.booking.persistence.TravelBookingRepository;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
import io.temporal.client.WorkflowClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executors;

/**
 * Wires {@link TravelBookingProgressionService}, {@link BookingCreationHandler}, and
 * {@link BookingCancellationHandler}. {@link WorkflowClient} itself comes from
 * {@code temporal-spring-boot-starter}'s own auto-configuration (this service only starts and
 * signals Workflow Executions, it never registers a Worker).
 */
@Configuration
public class BookingWebConfig {

    @Bean
    public TravelBookingProgressionService travelBookingProgressionService(final TravelBookingRepository travelBookingRepository) {
        return new TravelBookingProgressionService(travelBookingRepository);
    }

    /**
     * Backs {@link BookingCreationHandler}'s blocking {@code WorkflowClient.start} bridge and
     * {@link BookingCancellationHandler}'s blocking signal bridge (see there for why they can't
     * stay on the WebFlux event loop). A virtual-thread-per-task executor instead of
     * {@code Schedulers.boundedElastic()}'s capped platform-thread pool: both calls are
     * structurally unbounded the same way the Activity Worker's blocking {@code WebClient} calls
     * are meant to be (see {@code saga-orchestrator-temporal}'s
     * {@code VirtualThreadWorkerOptionsCustomizer}) — no CPU-bound work, no {@code synchronized}
     * blocks in either path. {@code destroyMethod = "dispose"} releases the executor on shutdown.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler bookingSagaStartScheduler() {
        return Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor(), "booking-saga-start");
    }

    @Bean
    public BookingCreationHandler bookingCreationHandler(
            final TravelBookingProgressionService travelBookingProgressionService,
            final WorkflowClient workflowClient,
            final Scheduler bookingSagaStartScheduler) {
        return new BookingCreationHandler(travelBookingProgressionService, workflowClient, bookingSagaStartScheduler);
    }

    @Bean
    public BookingCancellationHandler bookingCancellationHandler(
            final TravelBookingProgressionService travelBookingProgressionService,
            final WorkflowClient workflowClient,
            final Scheduler bookingSagaStartScheduler) {
        return new BookingCancellationHandler(travelBookingProgressionService, workflowClient, bookingSagaStartScheduler);
    }
}
