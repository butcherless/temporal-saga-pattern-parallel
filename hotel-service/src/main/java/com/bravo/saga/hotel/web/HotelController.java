package com.bravo.saga.hotel.web;

import com.bravo.saga.hotel.service.CancelHotelReservationRequest;
import com.bravo.saga.hotel.service.ConfirmHotelRequest;
import com.bravo.saga.hotel.service.HotelReservationProgressionService;
import com.bravo.saga.hotel.service.ReserveHotelRequest;
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

/** REST entry points for the hotel reservation lifecycle, called by the saga Workflow's HotelActivities. */
@RestController
@RequestMapping("/hotel-reservations")
@Tag(name = "Hotel reservations", description = "Reserve, confirm, and cancel a hotel reservation for a saga")
@Slf4j
public class HotelController {

    private final HotelReservationProgressionService hotelReservationProgressionService;

    public HotelController(final HotelReservationProgressionService hotelReservationProgressionService) {
        this.hotelReservationProgressionService = Objects.requireNonNull(
                hotelReservationProgressionService, "hotelReservationProgressionService must not be null");
    }

    @PostMapping
    @Operation(summary = "Reserve a hotel room", description = "Creates the hotel reservation. Idempotent by sagaId.")
    @ApiResponse(responseCode = "201", description = "Reservation created (or already existed)")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<HotelReservationResponseBody>> reserveHotel(@RequestBody final ReserveHotelRequestBody request) {
        log.debug("reserveHotel - {}", request);

        return this.hotelReservationProgressionService.reserveHotel(
                        new ReserveHotelRequest(request.sagaId(), request.toHotelBookingRequest(), Instant.now()))
                .map(reservation -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new HotelReservationResponseBody(reservation.id(), reservation.status())));
    }

    @PostMapping("/{sagaId}/confirm")
    @Operation(summary = "Confirm a hotel reservation",
            description = "Called once payment has completed. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation confirmed (or already was)")
    public Mono<ResponseEntity<HotelReservationResponseBody>> confirmHotel(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("confirmHotel - {}", sagaId);

        return this.hotelReservationProgressionService.confirmHotel(new ConfirmHotelRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new HotelReservationResponseBody(reservation.id(), reservation.status())));
    }

    @PostMapping("/{sagaId}/cancel")
    @Operation(summary = "Cancel a hotel reservation",
            description = "Compensation: called when a later saga step fails. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled (or already was)")
    public Mono<ResponseEntity<HotelReservationResponseBody>> cancelHotelReservation(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("cancelHotelReservation - {}", sagaId);

        return this.hotelReservationProgressionService.cancelHotelReservation(new CancelHotelReservationRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new HotelReservationResponseBody(reservation.id(), reservation.status())));
    }
}
