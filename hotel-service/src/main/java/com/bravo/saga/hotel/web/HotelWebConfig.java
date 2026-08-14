package com.bravo.saga.hotel.web;

import com.bravo.saga.hotel.persistence.HotelReservationRepository;
import com.bravo.saga.hotel.service.HotelReservationProgressionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires {@link HotelReservationProgressionService} for {@link HotelController}. */
@Configuration
public class HotelWebConfig {

    @Bean
    public HotelReservationProgressionService hotelReservationProgressionService(
            final HotelReservationRepository hotelReservationRepository) {
        return new HotelReservationProgressionService(hotelReservationRepository);
    }
}
