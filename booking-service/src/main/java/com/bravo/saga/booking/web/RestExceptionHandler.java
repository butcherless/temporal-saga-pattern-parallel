package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.BookingAlreadyConfirmedException;
import com.bravo.saga.booking.domain.BookingAlreadyFailedException;
import com.bravo.saga.booking.domain.BookingNotFoundException;
import com.bravo.saga.common.error.PermanentSagaException;
import com.bravo.saga.common.error.TemporarySagaException;
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
 * Shapes every endpoint's error responses as RFC 9457 {@link ProblemDetail}, same convention as
 * {@code temporal-saga-pattern}'s {@code order-service} {@code RestExceptionHandler}. Overrides
 * {@link ResponseEntityExceptionHandler#handleServerWebInputException} rather than adding a
 * competing {@code @ExceptionHandler(ServerWebInputException.class)} — see that class's own
 * javadoc for why a second, equally-specific mapping would be rejected at startup as ambiguous.
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

    @ExceptionHandler(PermanentSagaException.class)
    public Mono<ResponseEntity<Object>> handlePermanentSagaException(
            final PermanentSagaException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.UNPROCESSABLE_CONTENT, exchange);
    }

    @ExceptionHandler(TemporarySagaException.class)
    public Mono<ResponseEntity<Object>> handleTemporarySagaException(
            final TemporarySagaException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, exchange);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public Mono<ResponseEntity<Object>> handleBookingNotFoundException(
            final BookingNotFoundException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.NOT_FOUND, exchange);
    }

    @ExceptionHandler(BookingAlreadyConfirmedException.class)
    public Mono<ResponseEntity<Object>> handleBookingAlreadyConfirmedException(
            final BookingAlreadyConfirmedException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.CONFLICT, exchange);
    }

    @ExceptionHandler(BookingAlreadyFailedException.class)
    public Mono<ResponseEntity<Object>> handleBookingAlreadyFailedException(
            final BookingAlreadyFailedException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.CONFLICT, exchange);
    }
}
