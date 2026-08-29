package com.bravo.saga.car.web;

import com.bravo.saga.car.persistence.CarReservationRepository;
import com.bravo.saga.car.service.CarReservationProgressionService;
import com.bravo.saga.leg.web.LegReservationRestExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Wires {@link CarReservationProgressionService} for {@link CarController} and the shared leg exception handler. */
@Configuration
@Import(LegReservationRestExceptionHandler.class)
public class CarWebConfig {

    @Bean
    public CarReservationProgressionService carReservationProgressionService(
            final CarReservationRepository carReservationRepository) {
        return new CarReservationProgressionService(carReservationRepository);
    }
}
