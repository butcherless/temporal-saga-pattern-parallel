package com.bravo.saga.payment.web;

import com.bravo.saga.payment.service.CapturePaymentRequest;
import com.bravo.saga.payment.service.HoldPaymentRequest;
import com.bravo.saga.payment.service.PaymentHoldProgressionService;
import com.bravo.saga.payment.service.ReleasePaymentHoldRequest;
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

/** REST entry points for the payment hold lifecycle, called by the saga Workflow's PaymentActivities. */
@RestController
@RequestMapping("/payment-holds")
@Tag(name = "Payment holds", description = "Hold, capture, and release a payment hold for a saga")
@Slf4j
public class PaymentController {

    private final PaymentHoldProgressionService paymentHoldProgressionService;

    public PaymentController(final PaymentHoldProgressionService paymentHoldProgressionService) {
        this.paymentHoldProgressionService = Objects.requireNonNull(
                paymentHoldProgressionService, "paymentHoldProgressionService must not be null");
    }

    @PostMapping
    @Operation(summary = "Hold a payment", description = "Creates the payment hold. Idempotent by sagaId.")
    @ApiResponse(responseCode = "201", description = "Hold created (or already existed)")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public Mono<ResponseEntity<PaymentHoldResponseBody>> holdPayment(@RequestBody final HoldPaymentRequestBody request) {
        log.debug("holdPayment - {}", request);

        return this.paymentHoldProgressionService.holdPayment(
                        new HoldPaymentRequest(request.sagaId(), request.paymentAuthorizationToken(), Instant.now()))
                .map(hold -> ResponseEntity.status(HttpStatus.CREATED).body(new PaymentHoldResponseBody(hold.id(), hold.status())));
    }

    @PostMapping("/{sagaId}/capture")
    @Operation(summary = "Capture a payment hold",
            description = "Called once every leg is confirmed. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Hold captured (or already was)")
    public Mono<ResponseEntity<PaymentHoldResponseBody>> capturePayment(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the hold's id") final UUID sagaId) {
        log.debug("capturePayment - {}", sagaId);

        return this.paymentHoldProgressionService.capturePayment(new CapturePaymentRequest(sagaId, Instant.now()))
                .map(hold -> ResponseEntity.ok(new PaymentHoldResponseBody(hold.id(), hold.status())));
    }

    @PostMapping("/{sagaId}/release")
    @Operation(summary = "Release a payment hold",
            description = "Compensation: called when a later saga step fails before capture. Idempotent by sagaId.")
    @ApiResponse(responseCode = "200", description = "Hold released (or already was)")
    public Mono<ResponseEntity<PaymentHoldResponseBody>> releasePaymentHold(
            @PathVariable("sagaId") @Parameter(description = "The saga id, also the hold's id") final UUID sagaId) {
        log.debug("releasePaymentHold - {}", sagaId);

        return this.paymentHoldProgressionService.releasePaymentHold(new ReleasePaymentHoldRequest(sagaId, Instant.now()))
                .map(hold -> ResponseEntity.ok(new PaymentHoldResponseBody(hold.id(), hold.status())));
    }
}
