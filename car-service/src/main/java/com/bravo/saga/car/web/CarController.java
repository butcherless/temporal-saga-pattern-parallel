package com.bravo.saga.car.web;

import com.bravo.saga.car.service.CancelCarReservationRequest;
import com.bravo.saga.car.service.CarReservationProgressionService;
import com.bravo.saga.car.service.ConfirmCarRequest;
import com.bravo.saga.car.service.ReserveCarRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** REST entry points for the car reservation lifecycle, called by the saga Workflow's CarActivities. */
@RestController
@RequestMapping("/car-reservations")
@Tag(name = "Car reservations", description = "Reserve, confirm, and cancel a car reservation for a saga")
@Slf4j
public class CarController {

    private final CarReservationProgressionService carReservationProgressionService;
    private final int minSimulatedDelaySeconds;
    private final int maxSimulatedDelaySeconds;

    public CarController(final CarReservationProgressionService carReservationProgressionService,
            @Value("${car-service.simulated-delay.min-seconds:10}") final int minSimulatedDelaySeconds,
            @Value("${car-service.simulated-delay.max-seconds:20}") final int maxSimulatedDelaySeconds) {
        this.carReservationProgressionService = Objects.requireNonNull(
                carReservationProgressionService, "carReservationProgressionService must not be null");
        this.minSimulatedDelaySeconds = minSimulatedDelaySeconds;
        this.maxSimulatedDelaySeconds = maxSimulatedDelaySeconds;
    }

    private Duration simulatedProcessingDelay() {
        if (this.maxSimulatedDelaySeconds <= this.minSimulatedDelaySeconds) {
            return Duration.ofSeconds(this.minSimulatedDelaySeconds);
        }
        return Duration.ofSeconds(
                ThreadLocalRandom.current().nextLong(this.minSimulatedDelaySeconds, this.maxSimulatedDelaySeconds + 1));
    }

    @PostMapping
    @Operation(summary = "Reserve a car", description = "Creates the car reservation. Idempotent by sagaId.")
    @ApiResponse(responseCode = "201", description = "Reservation created (or already existed)")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<CarReservationResponseBody>> reserveCar(@RequestBody final ReserveCarRequestBody request) {
        log.debug("reserveCar - {}", request);

        return this.carReservationProgressionService.reserveCar(
                        new ReserveCarRequest(request.sagaId(), request.toCarBookingRequest(), Instant.now()))
                .map(reservation -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new CarReservationResponseBody(reservation.id(), reservation.status())))
                .delayElement(simulatedProcessingDelay());
    }

    @PostMapping("/{sagaId}/confirm")
    @Operation(summary = "Confirm a car reservation",
            description = "Called once payment has completed. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation confirmed (or already was)")
    public Mono<ResponseEntity<CarReservationResponseBody>> confirmCar(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("confirmCar - {}", sagaId);

        return this.carReservationProgressionService.confirmCar(new ConfirmCarRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new CarReservationResponseBody(reservation.id(), reservation.status())))
                .delayElement(simulatedProcessingDelay());
    }

    @PostMapping("/{sagaId}/cancel")
    @Operation(summary = "Cancel a car reservation",
            description = "Compensation: called when a later saga step fails. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled (or already was)")
    public Mono<ResponseEntity<CarReservationResponseBody>> cancelCarReservation(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("cancelCarReservation - {}", sagaId);

        return this.carReservationProgressionService.cancelCarReservation(new CancelCarReservationRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new CarReservationResponseBody(reservation.id(), reservation.status())))
                .delayElement(simulatedProcessingDelay());
    }
}
