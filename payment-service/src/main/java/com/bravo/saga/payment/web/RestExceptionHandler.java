package com.bravo.saga.payment.web;

import com.bravo.saga.payment.domain.IllegalPaymentHoldTransitionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * Shapes the payment hold endpoints' error responses as RFC 9457 {@link ProblemDetail}, same
 * convention as {@code booking-service}'s own {@code RestExceptionHandler}.
 *
 * <p>{@code PaymentHoldProgressionService} signals "no hold for this sagaId" as a plain
 * {@link IllegalStateException} (see its {@code loadHold}, pinned by
 * {@code PaymentHoldProgressionServiceTest}) rather than a dedicated not-found exception type —
 * mapped here to 404 since that's the only site in this service that throws it.
 */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected Mono<ResponseEntity<Object>> handleServerWebInputException(
            final ServerWebInputException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final ServerWebExchange exchange) {
        final Throwable cause = ex.getMostSpecificCause();
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, cause.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, headers, status, exchange);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Object>> handleIllegalStateException(
            final IllegalStateException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.NOT_FOUND, exchange);
    }

    @ExceptionHandler(IllegalPaymentHoldTransitionException.class)
    public Mono<ResponseEntity<Object>> handleIllegalPaymentHoldTransitionException(
            final IllegalPaymentHoldTransitionException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.CONFLICT, exchange);
    }
}
