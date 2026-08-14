package com.bravo.saga.orchestrator.activities;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.UUID;

/** Calls {@code payment-service} directly over HTTP; no Kafka involved. */
@Slf4j
@Component
public class PaymentActivitiesImpl implements PaymentActivities {

    private final WebClient paymentWebClient;

    public PaymentActivitiesImpl(@Qualifier("paymentWebClient") final WebClient paymentWebClient) {
        this.paymentWebClient = Objects.requireNonNull(paymentWebClient, "paymentWebClient must not be null");
    }

    @Override
    public void holdPayment(final UUID sagaId,
            final String paymentAuthorizationToken) {
        log.debug("holdPayment - sagaId={}", sagaId);
        this.paymentWebClient.post()
                .uri("/payment-holds")
                .bodyValue(new HoldPaymentRequest(sagaId, paymentAuthorizationToken))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void capturePayment(final UUID sagaId) {
        log.debug("capturePayment - sagaId={}", sagaId);
        this.paymentWebClient.post()
                .uri("/payment-holds/{sagaId}/capture", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void releasePaymentHold(final UUID sagaId) {
        log.debug("releasePaymentHold - sagaId={}", sagaId);
        this.paymentWebClient.post()
                .uri("/payment-holds/{sagaId}/release", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
