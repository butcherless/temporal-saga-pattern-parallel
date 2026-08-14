package com.bravo.saga.car.web;

import com.bravo.saga.car.persistence.CarReservationRepository;
import com.bravo.saga.car.service.CarReservationProgressionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires {@link CarReservationProgressionService} for {@link CarController}. */
@Configuration
public class CarWebConfig {

    @Bean
    public CarReservationProgressionService carReservationProgressionService(
            final CarReservationRepository carReservationRepository) {
        return new CarReservationProgressionService(carReservationRepository);
    }
}
