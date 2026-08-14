package com.bravo.saga.flight.web;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.flight.domain.FlightReservation;
import com.bravo.saga.flight.domain.IllegalReservationTransitionException;
import com.bravo.saga.flight.domain.ReservationStatus;
import com.bravo.saga.flight.service.FlightReservationProgressionService;
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
class FlightControllerTest {

    @Mock
    private FlightReservationProgressionService flightReservationProgressionService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToController(new FlightController(this.flightReservationProgressionService))
                .controllerAdvice(new RestExceptionHandler())
                .build();
    }

    private static FlightBookingRequest sampleBooking() {
        return new FlightBookingRequest("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID());
    }

    private static String sampleRequestJson(final UUID sagaId,
            final FlightBookingRequest booking) {
        return """
                {"sagaId":"%s","flightNumber":"%s","originAirportCode":"%s","destinationAirportCode":"%s",\
                "departureDate":"%s","passengerCount":%d,"offerReference":"%s"}"""
                .formatted(sagaId, booking.flightNumber(), booking.originAirportCode(), booking.destinationAirportCode(),
                        booking.departureDate(), booking.passengerCount(), booking.offerReference());
    }

    @Test
    void reserveFlightReturns201WithTheReservation() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation reservation = FlightReservation.reserve(sagaId, sampleBooking(), Instant.now());
        when(this.flightReservationProgressionService.reserveFlight(any())).thenReturn(Mono.just(reservation));

        this.client.post().uri("/flight-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequestJson(sagaId, sampleBooking()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.RESERVED.name());
    }

    @Test
    void reserveFlightRejectsAnInvalidRequestBodyWithAProblemDetail() {
        final ResponseSpec response = this.client.post().uri("/flight-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sagaId":"%s","flightNumber":null,"originAirportCode":"MAD",\
                        "destinationAirportCode":"CDG","departureDate":"2026-09-10","passengerCount":2,\
                        "offerReference":"%s"}""".formatted(UUID.randomUUID(), UUID.randomUUID()))
                .exchange();

        assertProblemDetail(response, 400, "flightNumber must not be null");
    }

    @Test
    void reserveFlightReturns404WhenNoReservationExists() {
        when(this.flightReservationProgressionService.reserveFlight(any()))
                .thenReturn(Mono.error(new IllegalStateException("Flight reservation not found: %s".formatted(UUID.randomUUID()))));

        final ResponseSpec response = this.client.post().uri("/flight-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequestJson(UUID.randomUUID(), sampleBooking()))
                .exchange();

        response.expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void confirmFlightReturns200WithTheConfirmedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation confirmed = FlightReservation.reserve(sagaId, sampleBooking(), Instant.now()).confirm(Instant.now());
        when(this.flightReservationProgressionService.confirmFlight(any())).thenReturn(Mono.just(confirmed));

        this.client.post().uri("/flight-reservations/{sagaId}/confirm", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.CONFIRMED.name());
    }

    @Test
    void confirmFlightReturns409WhenTheReservationCannotLegallyBeConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        when(this.flightReservationProgressionService.confirmFlight(any()))
                .thenReturn(Mono.error(new IllegalReservationTransitionException(ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED)));

        final ResponseSpec response = this.client.post().uri("/flight-reservations/{sagaId}/confirm", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition flight reservation from CANCELLED to CONFIRMED");
    }

    @Test
    void cancelFlightReservationReturns200WithTheCancelledStatus() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation cancelled = FlightReservation.reserve(sagaId, sampleBooking(), Instant.now()).cancel(Instant.now());
        when(this.flightReservationProgressionService.cancelFlightReservation(any())).thenReturn(Mono.just(cancelled));

        this.client.post().uri("/flight-reservations/{sagaId}/cancel", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.CANCELLED.name());
    }

    @Test
    void cancelFlightReservationReturns409WhenTheReservationCannotLegallyBeCancelled() {
        final UUID sagaId = UUID.randomUUID();
        when(this.flightReservationProgressionService.cancelFlightReservation(any()))
                .thenReturn(Mono.error(new IllegalReservationTransitionException(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)));

        final ResponseSpec response = this.client.post().uri("/flight-reservations/{sagaId}/cancel", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition flight reservation from CONFIRMED to CANCELLED");
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
