package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.BookingAlreadyConfirmedException;
import com.bravo.saga.booking.domain.BookingAlreadyFailedException;
import com.bravo.saga.booking.domain.BookingNotFoundException;
import com.bravo.saga.booking.domain.TravelBooking;
import com.bravo.saga.booking.domain.TravelBookingStatus;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Adapter-layer unit test for {@link BookingController} only — {@link BookingCreationHandler},
 * {@link BookingCancellationHandler}, and {@link TravelBookingProgressionService} are mocked, and
 * {@link WebTestClient} is bound directly to a manually-constructed controller instance rather than
 * a full Spring context. Same shape as {@code temporal-saga-pattern}'s own {@code OrderControllerTest}.
 * Covers every HTTP status code the three operations declare via {@code @ApiResponse}
 * (docs/travel-booking/controller-layer-plan.md): 202, 200, 400, 409, 404.
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingCreationHandler bookingCreationHandler;

    @Mock
    private BookingCancellationHandler bookingCancellationHandler;

    @Mock
    private TravelBookingProgressionService travelBookingProgressionService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToController(
                        new BookingController(this.bookingCreationHandler, this.bookingCancellationHandler, this.travelBookingProgressionService))
                .controllerAdvice(new RestExceptionHandler())
                .build();
    }

    // ---- POST /travel-bookings (buy) ----

    @Test
    void buyPackageReturns202AcceptedWhenANewBookingIsCreated() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCreationHandler.createBooking(any())).thenReturn(Mono.just(
                new BookingCreationHandler.BookingCreationOutcome(
                        new CreateBookingResponseBody(sagaId, "biz-2026-08-13-0001", TravelBookingStatus.PENDING),
                        true)));

        this.client.post().uri("/travel-bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBuyRequestJson("biz-2026-08-13-0001"))
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.businessKey").isEqualTo("biz-2026-08-13-0001")
                .jsonPath("$.status").isEqualTo(TravelBookingStatus.PENDING.name());
    }

    @Test
    void buyPackageReturns200WhenABookingForThisBusinessKeyAlreadyExisted() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCreationHandler.createBooking(any())).thenReturn(Mono.just(
                new BookingCreationHandler.BookingCreationOutcome(
                        new CreateBookingResponseBody(sagaId, "biz-2026-08-13-0001", TravelBookingStatus.CONFIRMED),
                        false)));

        this.client.post().uri("/travel-bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBuyRequestJson("biz-2026-08-13-0001"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(TravelBookingStatus.CONFIRMED.name());
    }

    @Test
    void buyPackageReturns400WhenTheRequestBodyIsInvalid() {
        final ResponseSpec response = this.client.post().uri("/travel-bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "operatorId": "",
                          "paymentAuthorizationToken": "pat_9f3e2b7c8a1d4e5f",
                          "flight": {"flightNumber":"IB6275","originAirportCode":"MAD","destinationAirportCode":"CDG","departureDate":"2026-09-10","passengerCount":2,"offerReference":"b6e1a1d2-4b9e-4a3a-9c2e-6f2b8b1a9e10"},
                          "hotel": {"hotelCode":"HTL-PARIS-014","roomType":"DOUBLE_DELUXE","checkInDate":"2026-09-10","checkOutDate":"2026-09-14","guestCount":2,"offerReference":"c7f2b2e3-5c0f-4b4b-8d3f-7a3c9c2b0f21"},
                          "car": {"carCategory":"COMPACT","pickupLocation":"CDG","pickupDate":"2026-09-10","dropoffDate":"2026-09-14","offerReference":"d8a3c3f4-6d1a-4c5c-9e4a-8b4d0d3c1f32"}
                        }""")
                .exchange();

        assertProblemDetail(response, 400, "operatorId must not be blank");
    }

    // ---- POST /travel-bookings/{sagaId}/cancel ----

    @Test
    void cancelPurchaseReturns202AcceptedWhenCancellationIsStartedFromPending() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCancellationHandler.cancelBooking(any(), any()))
                .thenReturn(Mono.just(new BookingStatusResponseBody(sagaId, TravelBookingStatus.PENDING)));

        this.client.post().uri("/travel-bookings/{sagaId}/cancel", sagaId)
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(TravelBookingStatus.PENDING.name());
    }

    @Test
    void cancelPurchaseAcceptsAnExplicitReasonBody() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCancellationHandler.cancelBooking(any(), any()))
                .thenReturn(Mono.just(new BookingStatusResponseBody(sagaId, TravelBookingStatus.PENDING)));

        this.client.post().uri("/travel-bookings/{sagaId}/cancel", sagaId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"reason":"customer changed mind"}""")
                .exchange()
                .expectStatus().isEqualTo(202);
    }

    @Test
    void cancelPurchaseReturns200WhenTheBookingWasAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCancellationHandler.cancelBooking(any(), any()))
                .thenReturn(Mono.just(new BookingStatusResponseBody(sagaId, TravelBookingStatus.CANCELLED)));

        this.client.post().uri("/travel-bookings/{sagaId}/cancel", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(TravelBookingStatus.CANCELLED.name());
    }

    @Test
    void cancelPurchaseReturns409WhenTheBookingIsAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCancellationHandler.cancelBooking(any(), any()))
                .thenReturn(Mono.error(new BookingAlreadyConfirmedException(sagaId)));

        final ResponseSpec response = this.client.post().uri("/travel-bookings/{sagaId}/cancel", sagaId).exchange();

        assertProblemDetail(response, 409,
                "Booking " + sagaId + " is already confirmed and paid; cancellation after confirmation requires a refund, which is not yet supported");
    }

    @Test
    void cancelPurchaseReturns409WhenTheBookingAlreadyFailed() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCancellationHandler.cancelBooking(any(), any()))
                .thenReturn(Mono.error(new BookingAlreadyFailedException(sagaId)));

        final ResponseSpec response = this.client.post().uri("/travel-bookings/{sagaId}/cancel", sagaId).exchange();

        assertProblemDetail(response, 409, "Booking " + sagaId + " already ended due to a step failure, not a cancellation request");
    }

    @Test
    void cancelPurchaseReturns404WhenNoBookingExistsForTheSagaId() {
        final UUID sagaId = UUID.randomUUID();
        when(this.bookingCancellationHandler.cancelBooking(any(), any()))
                .thenReturn(Mono.error(new BookingNotFoundException(sagaId)));

        final ResponseSpec response = this.client.post().uri("/travel-bookings/{sagaId}/cancel", sagaId).exchange();

        assertProblemDetail(response, 404, "No booking found for sagaId " + sagaId);
    }

    // ---- POST /travel-bookings/{sagaId}/confirm (internal) ----

    @Test
    void confirmBookingReturns200WithTheConfirmedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking confirmedBooking = TravelBooking.create(sagaId, "biz-2026-08-13-0001", "agency-042", Instant.now())
                .confirm(Instant.now());
        when(this.travelBookingProgressionService.confirmBooking(any())).thenReturn(Mono.just(confirmedBooking));

        this.client.post().uri("/travel-bookings/{sagaId}/confirm", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(TravelBookingStatus.CONFIRMED.name());
    }

    private static String validBuyRequestJson(final String businessKey) {
        return """
                {
                  "operatorId": "agency-042",
                  "paymentAuthorizationToken": "pat_9f3e2b7c8a1d4e5f",
                  "businessKey": "%s",
                  "flight": {"flightNumber":"IB6275","originAirportCode":"MAD","destinationAirportCode":"CDG","departureDate":"2026-09-10","passengerCount":2,"offerReference":"b6e1a1d2-4b9e-4a3a-9c2e-6f2b8b1a9e10"},
                  "hotel": {"hotelCode":"HTL-PARIS-014","roomType":"DOUBLE_DELUXE","checkInDate":"2026-09-10","checkOutDate":"2026-09-14","guestCount":2,"offerReference":"c7f2b2e3-5c0f-4b4b-8d3f-7a3c9c2b0f21"},
                  "car": {"carCategory":"COMPACT","pickupLocation":"CDG","pickupDate":"2026-09-10","dropoffDate":"2026-09-14","offerReference":"d8a3c3f4-6d1a-4c5c-9e4a-8b4d0d3c1f32"}
                }""".formatted(businessKey);
    }

    /**
     * Shared shape behind every {@code ProblemDetail} assertion above (400 for bad input, 404 for
     * an unknown sagaId, 409 for an illegal cancel): status code, {@code application/problem+json}
     * content type, and the {@code $.status}/{@code $.detail} body fields.
     */
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
