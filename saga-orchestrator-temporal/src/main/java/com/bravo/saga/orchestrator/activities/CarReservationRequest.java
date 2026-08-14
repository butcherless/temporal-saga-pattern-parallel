package com.bravo.saga.orchestrator.activities;

import java.time.LocalDate;
import java.util.UUID;

/** Body sent to {@code car-service}'s {@code POST /car-reservations}. */
record CarReservationRequest(UUID sagaId,
        String carCategory,
        String pickupLocation,
        LocalDate pickupDate,
        LocalDate dropoffDate,
        UUID offerReference) {
}
