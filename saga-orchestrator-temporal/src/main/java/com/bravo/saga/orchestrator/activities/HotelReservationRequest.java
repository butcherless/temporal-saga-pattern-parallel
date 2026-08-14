package com.bravo.saga.orchestrator.activities;

import java.time.LocalDate;
import java.util.UUID;

/** Body sent to {@code hotel-service}'s {@code POST /hotel-reservations}. */
record HotelReservationRequest(UUID sagaId,
        String hotelCode,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer guestCount,
        UUID offerReference) {
}
