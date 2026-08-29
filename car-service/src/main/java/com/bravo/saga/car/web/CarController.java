package com.bravo.saga.car.web;

import com.bravo.saga.car.service.CarReservationProgressionService;
import com.bravo.saga.common.workflow.CarBookingRequest;
import com.bravo.saga.leg.web.AbstractLegReservationController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the car reservation lifecycle, called by the saga Workflow's CarActivities.
 * The reserve/confirm/cancel handlers are inherited from {@link AbstractLegReservationController};
 * this class only binds the car types and its own path/tag/delay config.
 */
@RestController
@RequestMapping("/car-reservations")
@Tag(name = "Car reservations", description = "Reserve, confirm, and cancel a car reservation for a saga")
public class CarController
        extends AbstractLegReservationController<CarBookingRequest, ReserveCarRequestBody> {

    public CarController(final CarReservationProgressionService carReservationProgressionService,
            @Value("${car-service.simulated-delay.min-seconds:10}") final int minSimulatedDelaySeconds,
            @Value("${car-service.simulated-delay.max-seconds:20}") final int maxSimulatedDelaySeconds) {
        super(carReservationProgressionService, minSimulatedDelaySeconds, maxSimulatedDelaySeconds);
    }
}
