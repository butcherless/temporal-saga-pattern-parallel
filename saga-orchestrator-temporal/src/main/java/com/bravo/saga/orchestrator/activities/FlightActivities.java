package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Activities calling {@code flight-service}'s REST endpoints directly (no Kafka involved) — not
 * designed yet, see this module's pom.xml description. Takes the whole {@link FlightBookingRequest}
 * rather than flattening its fields into separate parameters, since that type already exists for
 * this exact purpose in the Workflow input.
 */
@ActivityInterface
public interface FlightActivities {

    @ActivityMethod
    void reserveFlight(UUID sagaId,
            FlightBookingRequest flightBooking);

    @ActivityMethod
    void confirmFlight(UUID sagaId);

    /** Compensation: cancels the reservation. */
    @ActivityMethod
    void cancelFlightReservation(UUID sagaId);
}
