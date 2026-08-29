package com.bravo.saga.flight.web;

import com.bravo.saga.flight.persistence.FlightReservationRepository;
import com.bravo.saga.flight.service.FlightReservationProgressionService;
import com.bravo.saga.leg.web.LegReservationRestExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Wires {@link FlightReservationProgressionService} for {@link FlightController} and the shared leg exception handler. */
@Configuration
@Import(LegReservationRestExceptionHandler.class)
public class FlightWebConfig {

    @Bean
    public FlightReservationProgressionService flightReservationProgressionService(
            final FlightReservationRepository flightReservationRepository) {
        return new FlightReservationProgressionService(flightReservationRepository);
    }
}
