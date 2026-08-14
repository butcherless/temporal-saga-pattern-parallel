package com.bravo.saga.orchestrator.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/** Activities calling {@code booking-service}'s REST endpoints — see docs/travel-booking/controller-layer-plan.md. */
@ActivityInterface
public interface BookingActivities {

    /** Calls {@code POST /travel-bookings/{sagaId}/confirm} — main use case, step 8. */
    @ActivityMethod
    void confirmBooking(UUID sagaId);
}
