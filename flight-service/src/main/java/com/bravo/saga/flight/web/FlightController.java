package com.bravo.saga.flight.web;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.flight.service.FlightReservationProgressionService;
import com.bravo.saga.leg.web.AbstractLegReservationController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the flight reservation lifecycle, called by the saga Workflow's
 * FlightActivities. The reserve/confirm/cancel handlers are inherited from
 * {@link AbstractLegReservationController}; this class only binds the flight types and its own
 * path/tag/delay config.
 */
@RestController
@RequestMapping("/flight-reservations")
@Tag(name = "Flight reservations", description = "Reserve, confirm, and cancel a flight reservation for a saga")
public class FlightController
        extends AbstractLegReservationController<FlightBookingRequest, ReserveFlightRequestBody> {

    public FlightController(final FlightReservationProgressionService flightReservationProgressionService,
            @Value("${flight-service.simulated-delay.min-seconds:10}") final int minSimulatedDelaySeconds,
            @Value("${flight-service.simulated-delay.max-seconds:20}") final int maxSimulatedDelaySeconds) {
        super(flightReservationProgressionService, minSimulatedDelaySeconds, maxSimulatedDelaySeconds);
    }
}
