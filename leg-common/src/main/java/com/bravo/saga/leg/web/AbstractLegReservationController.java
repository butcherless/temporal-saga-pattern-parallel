package com.bravo.saga.leg.web;

import com.bravo.saga.common.workflow.LegBookingRequest;
import com.bravo.saga.leg.service.LegReservationProgressionService;
import com.bravo.saga.leg.service.ReserveLegRequest;
import com.bravo.saga.leg.service.SagaStepRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The reserve/confirm/cancel REST handlers every leg exposes, identical bar the request-body type
 * and a few OpenAPI strings. Each leg subclasses this as a thin {@code @RestController} carrying
 * its own {@code @RequestMapping} path, {@code @Tag}, and {@code @Value}-bound simulated-delay
 * config; the concrete class also fixes {@code B}/{@code BODY}, which is what lets Spring resolve
 * the generic {@code @RequestBody} parameter.
 *
 * <p>Not a {@code @RestController} itself and not component-scanned — only the concrete subclasses are.
 *
 * @param <B>    the leg's booking-request type
 * @param <BODY> the leg's {@code POST /{leg}-reservations} request body
 */
@Slf4j
public abstract class AbstractLegReservationController<B extends LegBookingRequest, BODY extends ReserveLegRequestBody<B>> {

    private final LegReservationProgressionService<?, B> progressionService;
    private final int minSimulatedDelaySeconds;
    private final int maxSimulatedDelaySeconds;

    protected AbstractLegReservationController(final LegReservationProgressionService<?, B> progressionService,
            final int minSimulatedDelaySeconds,
            final int maxSimulatedDelaySeconds) {
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService must not be null");
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
    @Operation(summary = "Reserve the leg", description = "Creates the reservation. Idempotent by sagaId.")
    @ApiResponse(responseCode = "201", description = "Reservation created (or already existed)")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<ReservationResponseBody>> reserve(@RequestBody final BODY request) {
        log.debug("reserve - {}", request);

        return this.progressionService.reserve(
                        new ReserveLegRequest<>(request.sagaId(), request.toBookingRequest(), Instant.now()))
                .map(reservation -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new ReservationResponseBody(reservation.id(), reservation.status())))
                .delayElement(simulatedProcessingDelay());
    }

    @PostMapping("/{sagaId}/confirm")
    @Operation(summary = "Confirm the leg reservation",
            description = "Called once payment has completed. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation confirmed (or already was)")
    public Mono<ResponseEntity<ReservationResponseBody>> confirm(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("confirm - {}", sagaId);

        return this.progressionService.confirm(new SagaStepRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new ReservationResponseBody(reservation.id(), reservation.status())))
                .delayElement(simulatedProcessingDelay());
    }

    @PostMapping("/{sagaId}/cancel")
    @Operation(summary = "Cancel the leg reservation",
            description = "Compensation: called when a later saga step fails. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled (or already was)")
    public Mono<ResponseEntity<ReservationResponseBody>> cancel(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the reservation's id") final UUID sagaId) {
        log.debug("cancel - {}", sagaId);

        return this.progressionService.cancel(new SagaStepRequest(sagaId, Instant.now()))
                .map(reservation -> ResponseEntity.ok(new ReservationResponseBody(reservation.id(), reservation.status())))
                .delayElement(simulatedProcessingDelay());
    }
}
