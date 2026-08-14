package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.CarBookingRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Activities calling {@code car-service}'s REST endpoints directly (no Kafka involved) — not
 * designed yet, see this module's pom.xml description.
 */
@ActivityInterface
public interface CarActivities {

    @ActivityMethod
    void reserveCar(UUID sagaId,
            CarBookingRequest carBooking);

    @ActivityMethod
    void confirmCar(UUID sagaId);

    /** Compensation: cancels the reservation. */
    @ActivityMethod
    void cancelCarReservation(UUID sagaId);
}
