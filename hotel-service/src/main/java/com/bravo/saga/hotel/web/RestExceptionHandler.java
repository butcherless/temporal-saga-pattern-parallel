package com.bravo.saga.hotel.web;

import com.bravo.saga.hotel.domain.IllegalReservationTransitionException;
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
 * Shapes the reservation endpoints' error responses as RFC 9457 {@link ProblemDetail}, same
 * convention as {@code booking-service}'s own {@code RestExceptionHandler}.
 *
 * <p>{@code HotelReservationProgressionService} signals "no reservation for this sagaId" as a
 * plain {@link IllegalStateException} (see its {@code loadReservation}, pinned by
 * {@code HotelReservationProgressionServiceTest}) rather than a dedicated not-found exception
 * type — mapped here to 404 since that's the only site in this service that throws it.
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

    @ExceptionHandler(IllegalReservationTransitionException.class)
    public Mono<ResponseEntity<Object>> handleIllegalReservationTransitionException(
            final IllegalReservationTransitionException ex,
            final ServerWebExchange exchange) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return this.handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.CONFLICT, exchange);
    }
}
