package com.bravo.saga.booking.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Request body for {@code POST /travel-bookings} — docs/travel-booking/controller-layer-plan.md,
 * Operation 1. {@code businessKey} is the client's own idempotency key and is optional;
 * {@link BookingCreationHandler} generates one from the minted {@code sagaId} when omitted or
 * blank, same {@code hasNoBusinessKey()} pattern as {@code temporal-saga-pattern}'s
 * {@code CreateOrderRequestBody}.
 */
public record CreateBookingRequestBody(
        @Schema(example = "agency-042", requiredMode = Schema.RequiredMode.REQUIRED)
        String operatorId,
        @Schema(description = "The customer's payment authorization token, generated outside this saga",
                example = "pat_9f3e2b7c8a1d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
        String paymentAuthorizationToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FlightLegRequestBody flight,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        HotelLegRequestBody hotel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CarLegRequestBody car,
        @Schema(description = "Client-supplied idempotency key; generated from the minted sagaId when omitted", example = "biz-2026-08-13-0001")
        String businessKey) {

    public CreateBookingRequestBody {
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(paymentAuthorizationToken, "paymentAuthorizationToken must not be null");
        Objects.requireNonNull(flight, "flight must not be null");
        Objects.requireNonNull(hotel, "hotel must not be null");
        Objects.requireNonNull(car, "car must not be null");
        if (operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId must not be blank");
        }
        if (paymentAuthorizationToken.isBlank()) {
            throw new IllegalArgumentException("paymentAuthorizationToken must not be blank");
        }
    }

    public Boolean hasNoBusinessKey() {
        return !StringUtils.hasText(businessKey);
    }
}
