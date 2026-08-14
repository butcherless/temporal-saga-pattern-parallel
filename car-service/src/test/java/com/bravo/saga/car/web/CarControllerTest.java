package com.bravo.saga.car.web;

import com.bravo.saga.car.domain.CarReservation;
import com.bravo.saga.car.domain.IllegalReservationTransitionException;
import com.bravo.saga.car.domain.ReservationStatus;
import com.bravo.saga.car.service.CarReservationProgressionService;
import com.bravo.saga.common.workflow.CarBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarControllerTest {

    @Mock
    private CarReservationProgressionService carReservationProgressionService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToController(new CarController(this.carReservationProgressionService, 0, 0))
                .controllerAdvice(new RestExceptionHandler())
                .build();
    }

    private static CarBookingRequest sampleBooking() {
        return new CarBookingRequest("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID());
    }

    private static String sampleRequestJson(final UUID sagaId,
            final CarBookingRequest booking) {
        return """
                {"sagaId":"%s","carCategory":"%s","pickupLocation":"%s","pickupDate":"%s",\
                "dropoffDate":"%s","offerReference":"%s"}"""
                .formatted(sagaId, booking.carCategory(), booking.pickupLocation(), booking.pickupDate(),
                        booking.dropoffDate(), booking.offerReference());
    }

    @Test
    void reserveCarReturns201WithTheReservation() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation reservation = CarReservation.reserve(sagaId, sampleBooking(), Instant.now());
        when(this.carReservationProgressionService.reserveCar(any())).thenReturn(Mono.just(reservation));

        this.client.post().uri("/car-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequestJson(sagaId, sampleBooking()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.RESERVED.name());
    }

    @Test
    void reserveCarRejectsAnInvalidRequestBodyWithAProblemDetail() {
        final ResponseSpec response = this.client.post().uri("/car-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sagaId":"%s","carCategory":null,"pickupLocation":"CDG",\
                        "pickupDate":"2026-09-10","dropoffDate":"2026-09-14",\
                        "offerReference":"%s"}""".formatted(UUID.randomUUID(), UUID.randomUUID()))
                .exchange();

        assertProblemDetail(response, 400, "carCategory must not be null");
    }

    @Test
    void confirmCarReturns200WithTheConfirmedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation confirmed = CarReservation.reserve(sagaId, sampleBooking(), Instant.now()).confirm(Instant.now());
        when(this.carReservationProgressionService.confirmCar(any())).thenReturn(Mono.just(confirmed));

        this.client.post().uri("/car-reservations/{sagaId}/confirm", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.CONFIRMED.name());
    }

    @Test
    void confirmCarReturns409WhenTheReservationCannotLegallyBeConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        when(this.carReservationProgressionService.confirmCar(any()))
                .thenReturn(Mono.error(new IllegalReservationTransitionException(ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED)));

        final ResponseSpec response = this.client.post().uri("/car-reservations/{sagaId}/confirm", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition car reservation from CANCELLED to CONFIRMED");
    }

    @Test
    void cancelCarReservationReturns200WithTheCancelledStatus() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation cancelled = CarReservation.reserve(sagaId, sampleBooking(), Instant.now()).cancel(Instant.now());
        when(this.carReservationProgressionService.cancelCarReservation(any())).thenReturn(Mono.just(cancelled));

        this.client.post().uri("/car-reservations/{sagaId}/cancel", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.CANCELLED.name());
    }

    @Test
    void cancelCarReservationReturns409WhenTheReservationCannotLegallyBeCancelled() {
        final UUID sagaId = UUID.randomUUID();
        when(this.carReservationProgressionService.cancelCarReservation(any()))
                .thenReturn(Mono.error(new IllegalReservationTransitionException(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)));

        final ResponseSpec response = this.client.post().uri("/car-reservations/{sagaId}/cancel", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition car reservation from CONFIRMED to CANCELLED");
    }

    private static void assertProblemDetail(final ResponseSpec response,
            final int expectedStatus,
            final String expectedDetail) {
        response.expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(expectedStatus)
                .jsonPath("$.detail").isEqualTo(expectedDetail);
    }
}
