package com.bravo.saga.orchestrator.config;

import io.temporal.spring.boot.WorkerOptionsCustomizer;
import io.temporal.worker.WorkerOptions;
import org.springframework.stereotype.Component;

/**
 * Runs Activity task execution on virtual threads instead of the SDK's default platform-thread
 * pool. Every {@code *ActivitiesImpl} method (Flight/Hotel/Car/Payment/Booking) ends in exactly
 * one blocking {@code WebClient#block()} call and does no CPU-bound work in between — the
 * textbook virtual-thread case: one thread parked per in-flight HTTP call.
 *
 * <p>Scoped to the Activity worker only ({@link WorkerOptions.Builder#setUsingVirtualThreadsOnActivityWorker}
 * — not {@code setUsingVirtualThreads}, which would also flip Workflow task execution): Temporal's
 * deterministic-replay machinery for Workflow tasks stays on platform threads. Picked up
 * automatically by {@code temporal-spring-boot-starter} — any bean implementing
 * {@code WorkerOptionsCustomizer} is applied to every Worker it builds (there is only one,
 * {@code travel-booking-saga-task-queue}, per {@code application.yml}). Same convention as
 * {@code temporal-saga-pattern}'s own class of this name.
 */
@Component
public class VirtualThreadWorkerOptionsCustomizer implements WorkerOptionsCustomizer {

    @Override
    public WorkerOptions.Builder customize(
            final WorkerOptions.Builder optionsBuilder,
            final String workerName,
            final String taskQueue) {
        return optionsBuilder.setUsingVirtualThreadsOnActivityWorker(true);
    }
}
