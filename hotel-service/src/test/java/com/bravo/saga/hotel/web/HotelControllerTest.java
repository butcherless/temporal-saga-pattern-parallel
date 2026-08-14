package com.bravo.saga.hotel.web;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.hotel.domain.HotelReservation;
import com.bravo.saga.hotel.domain.IllegalReservationTransitionException;
import com.bravo.saga.hotel.domain.ReservationStatus;
import com.bravo.saga.hotel.service.HotelReservationProgressionService;
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
class HotelControllerTest {

    @Mock
    private HotelReservationProgressionService hotelReservationProgressionService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToController(new HotelController(this.hotelReservationProgressionService))
                .controllerAdvice(new RestExceptionHandler())
                .build();
    }

    private static HotelBookingRequest sampleBooking() {
        return new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID());
    }

    private static String sampleRequestJson(final UUID sagaId,
            final HotelBookingRequest booking) {
        return """
                {"sagaId":"%s","hotelCode":"%s","roomType":"%s","checkInDate":"%s",\
                "checkOutDate":"%s","guestCount":%d,"offerReference":"%s"}"""
                .formatted(sagaId, booking.hotelCode(), booking.roomType(), booking.checkInDate(),
                        booking.checkOutDate(), booking.guestCount(), booking.offerReference());
    }

    @Test
    void reserveHotelReturns201WithTheReservation() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation reservation = HotelReservation.reserve(sagaId, sampleBooking(), Instant.now());
        when(this.hotelReservationProgressionService.reserveHotel(any())).thenReturn(Mono.just(reservation));

        this.client.post().uri("/hotel-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleRequestJson(sagaId, sampleBooking()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.RESERVED.name());
    }

    @Test
    void reserveHotelRejectsAnInvalidRequestBodyWithAProblemDetail() {
        final ResponseSpec response = this.client.post().uri("/hotel-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sagaId":"%s","hotelCode":null,"roomType":"DOUBLE_DELUXE",\
                        "checkInDate":"2026-09-10","checkOutDate":"2026-09-14","guestCount":2,\
                        "offerReference":"%s"}""".formatted(UUID.randomUUID(), UUID.randomUUID()))
                .exchange();

        assertProblemDetail(response, 400, "hotelCode must not be null");
    }

    @Test
    void confirmHotelReturns200WithTheConfirmedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation confirmed = HotelReservation.reserve(sagaId, sampleBooking(), Instant.now()).confirm(Instant.now());
        when(this.hotelReservationProgressionService.confirmHotel(any())).thenReturn(Mono.just(confirmed));

        this.client.post().uri("/hotel-reservations/{sagaId}/confirm", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.CONFIRMED.name());
    }

    @Test
    void confirmHotelReturns409WhenTheReservationCannotLegallyBeConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationProgressionService.confirmHotel(any()))
                .thenReturn(Mono.error(new IllegalReservationTransitionException(ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED)));

        final ResponseSpec response = this.client.post().uri("/hotel-reservations/{sagaId}/confirm", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition hotel reservation from CANCELLED to CONFIRMED");
    }

    @Test
    void cancelHotelReservationReturns200WithTheCancelledStatus() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation cancelled = HotelReservation.reserve(sagaId, sampleBooking(), Instant.now()).cancel(Instant.now());
        when(this.hotelReservationProgressionService.cancelHotelReservation(any())).thenReturn(Mono.just(cancelled));

        this.client.post().uri("/hotel-reservations/{sagaId}/cancel", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(ReservationStatus.CANCELLED.name());
    }

    @Test
    void cancelHotelReservationReturns409WhenTheReservationCannotLegallyBeCancelled() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationProgressionService.cancelHotelReservation(any()))
                .thenReturn(Mono.error(new IllegalReservationTransitionException(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)));

        final ResponseSpec response = this.client.post().uri("/hotel-reservations/{sagaId}/cancel", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition hotel reservation from CONFIRMED to CANCELLED");
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
