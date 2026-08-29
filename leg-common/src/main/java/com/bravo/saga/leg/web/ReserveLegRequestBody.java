package com.bravo.saga.leg.web;

import com.bravo.saga.common.workflow.LegBookingRequest;

import java.util.UUID;

/**
 * The surface {@link AbstractLegReservationController} needs from a leg's {@code POST
 * /{leg}-reservations} request body: the saga id and a conversion to the saga-common leg type.
 * Implemented by each leg's {@code Reserve*RequestBody} record.
 *
 * @param <B> the leg's booking-request type ({@code FlightBookingRequest} etc.)
 */
public interface ReserveLegRequestBody<B extends LegBookingRequest> {

    UUID sagaId();

    /** Converts to the saga-common leg type the service layer consumes, delegating field validation to it. */
    B toBookingRequest();
}
