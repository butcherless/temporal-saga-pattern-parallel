package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBookingStatus;
import com.bravo.saga.booking.service.ConfirmBookingRequest;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * REST entry points for the travel-booking lifecycle — docs/travel-booking/controller-layer-plan.md.
 * Two client-facing operations (buy, cancel), plus one internal operation ({@code /confirm}) called
 * by the saga Workflow's {@code BookingActivities} once payment is captured (main use case, step 8) —
 * not part of the controller-layer plan's original 2-operation scope, added here because the happy
 * path has nothing else to call to reach {@code Confirmed}.
 */
@RestController
@RequestMapping("/travel-bookings")
@Tag(name = "Travel bookings", description = "Travel booking lifecycle — buy starts the saga, cancel signals it, confirm is invoked by it")
@Slf4j
public class BookingController {

    private final BookingCreationHandler bookingCreationHandler;
    private final BookingCancellationHandler bookingCancellationHandler;
    private final TravelBookingProgressionService travelBookingProgressionService;

    public BookingController(final BookingCreationHandler bookingCreationHandler,
            final BookingCancellationHandler bookingCancellationHandler,
            final TravelBookingProgressionService travelBookingProgressionService) {
        this.bookingCreationHandler = Objects.requireNonNull(bookingCreationHandler, "bookingCreationHandler must not be null");
        this.bookingCancellationHandler = Objects.requireNonNull(bookingCancellationHandler, "bookingCancellationHandler must not be null");
        this.travelBookingProgressionService = Objects.requireNonNull(travelBookingProgressionService, "travelBookingProgressionService must not be null");
    }

    @PostMapping
    @Operation(
            summary = "Buy a vacation package",
            description = "Mints a sagaId, creates the booking, and starts the travel-booking saga Workflow. "
                    + "Idempotent by businessKey.")
    @ApiResponse(responseCode = "202", description = "Booking accepted; the saga Workflow will proceed asynchronously")
    @ApiResponse(responseCode = "200", description = "A booking for this businessKey already existed; no new saga started")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<CreateBookingResponseBody>> buyPackage(@RequestBody final CreateBookingRequestBody request) {
        log.debug("buyPackage - {}", request);

        return this.bookingCreationHandler.createBooking(request)
                .map(outcome -> ResponseEntity.status(outcome.newlyCreated() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                        .body(outcome.body()));
    }

    @PostMapping("/{sagaId}/cancel")
    @Operation(
            summary = "Cancel the buy",
            description = "Only permitted while the booking has not yet reached Confirmed — cancelling a confirmed "
                    + "booking would require a refund, not yet supported. Idempotent by sagaId.")
    @ApiResponse(responseCode = "202", description = "Cancellation accepted; compensation started")
    @ApiResponse(responseCode = "200", description = "Booking was already cancelled; no new compensation triggered")
    @ApiResponse(responseCode = "409", description = "Booking already Confirmed or already Failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No booking with this sagaId",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<BookingStatusResponseBody>> cancelPurchase(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the booking's id") final UUID sagaId,
            @RequestBody(required = false) final CancelBookingRequestBody request) {
        log.debug("cancelPurchase - sagaId={}, request={}", sagaId, request);

        final CancelBookingRequestBody effectiveRequest = request == null ? new CancelBookingRequestBody(null) : request;
        return this.bookingCancellationHandler.cancelBooking(sagaId, effectiveRequest)
                .map(response -> ResponseEntity.status(response.status() == TravelBookingStatus.CANCELLED ? HttpStatus.OK : HttpStatus.ACCEPTED)
                        .body(response));
    }

    @PostMapping("/{sagaId}/confirm")
    @Operation(
            summary = "Confirm a booking",
            description = "Called by the travel-booking saga Workflow's BookingActivities once payment has been "
                    + "captured. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Booking confirmed (or already was)")
    public Mono<ResponseEntity<BookingStatusResponseBody>> confirmBooking(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the booking's id") final UUID sagaId) {
        log.debug("confirmBooking - {}", sagaId);

        return this.travelBookingProgressionService.confirmBooking(new ConfirmBookingRequest(sagaId, Instant.now()))
                .map(booking -> ResponseEntity.ok(new BookingStatusResponseBody(booking.id(), booking.status())));
    }
}
