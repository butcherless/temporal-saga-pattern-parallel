package com.bravo.saga.flight.web;

import com.bravo.saga.flight.persistence.FlightReservationRepository;
import com.bravo.saga.flight.service.FlightReservationProgressionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires {@link FlightReservationProgressionService} for {@link FlightController}. */
@Configuration
public class FlightWebConfig {

    @Bean
    public FlightReservationProgressionService flightReservationProgressionService(
            final FlightReservationRepository flightReservationRepository) {
        return new FlightReservationProgressionService(flightReservationRepository);
    }
}
