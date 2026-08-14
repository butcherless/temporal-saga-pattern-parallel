package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.CarBookingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.UUID;

/** Calls {@code car-service} directly over HTTP; no Kafka involved. */
@Slf4j
@Component
public class CarActivitiesImpl implements CarActivities {

    private final WebClient carWebClient;

    public CarActivitiesImpl(@Qualifier("carWebClient") final WebClient carWebClient) {
        this.carWebClient = Objects.requireNonNull(carWebClient, "carWebClient must not be null");
    }

    @Override
    public void reserveCar(final UUID sagaId,
            final CarBookingRequest carBooking) {
        log.debug("reserveCar - sagaId={}, carBooking={}", sagaId, carBooking);
        this.carWebClient.post()
                .uri("/car-reservations")
                .bodyValue(new CarReservationRequest(sagaId, carBooking.carCategory(), carBooking.pickupLocation(),
                        carBooking.pickupDate(), carBooking.dropoffDate(), carBooking.offerReference()))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void confirmCar(final UUID sagaId) {
        log.debug("confirmCar - sagaId={}", sagaId);
        this.carWebClient.post()
                .uri("/car-reservations/{sagaId}/confirm", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void cancelCarReservation(final UUID sagaId) {
        log.debug("cancelCarReservation - sagaId={}", sagaId);
        this.carWebClient.post()
                .uri("/car-reservations/{sagaId}/cancel", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
