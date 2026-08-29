package com.bravo.saga.hotel.web;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.hotel.service.HotelReservationProgressionService;
import com.bravo.saga.leg.web.AbstractLegReservationController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the hotel reservation lifecycle, called by the saga Workflow's
 * HotelActivities. The reserve/confirm/cancel handlers are inherited from
 * {@link AbstractLegReservationController}; this class only binds the hotel types and its own
 * path/tag/delay config.
 */
@RestController
@RequestMapping("/hotel-reservations")
@Tag(name = "Hotel reservations", description = "Reserve, confirm, and cancel a hotel reservation for a saga")
public class HotelController
        extends AbstractLegReservationController<HotelBookingRequest, ReserveHotelRequestBody> {

    public HotelController(final HotelReservationProgressionService hotelReservationProgressionService,
            @Value("${hotel-service.simulated-delay.min-seconds:10}") final int minSimulatedDelaySeconds,
            @Value("${hotel-service.simulated-delay.max-seconds:20}") final int maxSimulatedDelaySeconds) {
        super(hotelReservationProgressionService, minSimulatedDelaySeconds, maxSimulatedDelaySeconds);
    }
}
