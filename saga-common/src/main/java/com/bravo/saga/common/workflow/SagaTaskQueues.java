package com.bravo.saga.common.workflow;

/**
 * Task Queue names shared between the Workflow starter ({@code booking-service}) and the Worker
 * that polls them ({@code saga-orchestrator-temporal}).
 */
public final class SagaTaskQueues {

    public static final String TRAVEL_BOOKING_SAGA = "travel-booking-saga-task-queue";

    private SagaTaskQueues() {
    }
}
