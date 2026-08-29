package com.bravo.saga.hotel.web;

import com.bravo.saga.hotel.persistence.HotelReservationRepository;
import com.bravo.saga.hotel.service.HotelReservationProgressionService;
import com.bravo.saga.leg.web.LegReservationRestExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Wires {@link HotelReservationProgressionService} for {@link HotelController} and the shared leg exception handler. */
@Configuration
@Import(LegReservationRestExceptionHandler.class)
public class HotelWebConfig {

    @Bean
    public HotelReservationProgressionService hotelReservationProgressionService(
            final HotelReservationRepository hotelReservationRepository) {
        return new HotelReservationProgressionService(hotelReservationRepository);
    }
}
