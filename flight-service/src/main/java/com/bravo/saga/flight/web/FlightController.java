package com.bravo.saga.flight.web;

import com.bravo.saga.flight.service.CancelFlightReservationRequest;
import com.bravo.saga.flight.service.ConfirmFlightRequest;
import com.bravo.saga.flight.service.FlightReservationProgressionService;
import com.bravo.saga.flight.service.ReserveFlightRequest;
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

/** REST entry points for the flight reservation lifecycle, called by the saga Workflow's FlightActivities. */
@RestController
@RequestMapping("/flight-reservations")
@Tag(name = "Flight reservations", description = "Reserve, confirm, and cancel a flight reservation for a saga")
@Slf4j
public class FlightController {

    private final FlightReservationProgressionService flightReservationProgressionService;

    public FlightController(final FlightReservationProgressionService flightReservationProgressionService) {
        this.flightReservationProgressionService = Objects.requireNonNull(
                flightReservationProgressionService, "flightReservationProgressionService must not be null");
    }

    @PostMapping
    @Operation(summary = "Reserve a flight", description = "Creates the flight reservation. Idempotent by sagaId.")
    @ApiResponse(responseCode = "201", description = "Reservation created (or already existed)")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<FlightReservationResponseBody>> reserveFlight(@RequestBody final ReserveFlightRequestBody request) {
        log.debug("reserveFlight - {}", request);

        return this.flightReservationProgressionService.reserveFlight(
                        new ReserveFlightRequest(request.sagaId(), request.toFlightBookingRequest(), Instant.now()))
                .map(reservation -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new FlightReservationResponseBody(reservation.id(), reservation.status())));
    }

    @PostMapping("/{sagaId}/confirm")
    @Operation(summary = "Confirm a flight reservation",
            description = "Called once payment has completed. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation confirmed (or already was)")
    public Mono<ResponseEntity<FlightReservationResponseBody>> confirmFlight(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("confirmFlight - {}", sagaId);

        return this.flightReservationProgressionService.confirmFlight(new ConfirmFlightRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new FlightReservationResponseBody(reservation.id(), reservation.status())));
    }

    @PostMapping("/{sagaId}/cancel")
    @Operation(summary = "Cancel a flight reservation",
            description = "Compensation: called when a later saga step fails. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled (or already was)")
    public Mono<ResponseEntity<FlightReservationResponseBody>> cancelFlightReservation(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("cancelFlightReservation - {}", sagaId);

        return this.flightReservationProgressionService.cancelFlightReservation(new CancelFlightReservationRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new FlightReservationResponseBody(reservation.id(), reservation.status())));
    }
}
