package com.bravo.saga.common.workflow;

import java.util.UUID;

/**
 * The one thing the flight/hotel/car leg-booking records have in common and that the shared
 * reservation-progression logic needs: a binding {@code offerReference} obtained earlier from the
 * provider (business-definitions.md §5). Implemented by {@link FlightBookingRequest},
 * {@link HotelBookingRequest}, and {@link CarBookingRequest}.
 */
public interface LegBookingRequest {

    UUID offerReference();
}
