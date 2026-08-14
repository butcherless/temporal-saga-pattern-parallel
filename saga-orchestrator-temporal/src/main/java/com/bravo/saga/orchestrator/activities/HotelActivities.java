package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Activities calling {@code hotel-service}'s REST endpoints directly (no Kafka involved) — not
 * designed yet, see this module's pom.xml description.
 */
@ActivityInterface
public interface HotelActivities {

    @ActivityMethod
    void reserveHotel(UUID sagaId,
            HotelBookingRequest hotelBooking);

    @ActivityMethod
    void confirmHotel(UUID sagaId);

    /** Compensation: cancels the reservation. */
    @ActivityMethod
    void cancelHotelReservation(UUID sagaId);
}
