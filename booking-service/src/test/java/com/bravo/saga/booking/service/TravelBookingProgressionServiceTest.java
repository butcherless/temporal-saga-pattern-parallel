package com.bravo.saga.booking.service;

import com.bravo.saga.booking.domain.BookingAlreadyConfirmedException;
import com.bravo.saga.booking.domain.BookingAlreadyFailedException;
import com.bravo.saga.booking.domain.BookingNotFoundException;
import com.bravo.saga.booking.domain.IllegalTravelBookingTransitionException;
import com.bravo.saga.booking.domain.TravelBooking;
import com.bravo.saga.booking.domain.TravelBookingStatus;
import com.bravo.saga.booking.persistence.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Adapter-free unit test for {@link TravelBookingProgressionService} — {@link TravelBookingRepository}
 * is mocked. {@code createBooking}/{@code confirmBooking} back main use case steps 1 and 8
 * (docs/travel-booking/use-case-main.md); {@code validateCancellable} backs the cancel operation
 * and {@code findBooking} the status query (docs/travel-booking/controller-layer-plan.md,
 * Operation 2; use-case-alternatives.md A2).
 */
@ExtendWith(MockitoExtension.class)
class TravelBookingProgressionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");
    private static final String BUSINESS_KEY = "biz-2026-08-13-0001";
    private static final String OPERATOR_ID = "agency-042";

    @Mock
    private TravelBookingRepository travelBookingRepository;

    private TravelBookingProgressionService service;

    @BeforeEach
    void setUp() {
        this.service = new TravelBookingProgressionService(this.travelBookingRepository);
        lenient().when(this.travelBookingRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void findBookingReturnsTheBookingsCurrentState() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking existing = withStatus(sagaId, TravelBookingStatus.CONFIRMED);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.findBooking(sagaId))
                .assertNext(booking -> assertThat(booking).isEqualTo(existing))
                .verifyComplete();

        verify(this.travelBookingRepository, never()).save(any());
    }

    @Test
    void findBookingFailsWhenNoBookingExistsForTheSagaId() {
        final UUID sagaId = UUID.randomUUID();
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.findBooking(sagaId))
                .expectError(BookingNotFoundException.class)
                .verify();
    }

    @Test
    void createBookingCreatesANewBookingWhenNoneExistsForTheBusinessKey() {
        when(this.travelBookingRepository.findByBusinessKey(BUSINESS_KEY)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.createBooking(new CreateBookingRequest(UUID.randomUUID(), BUSINESS_KEY, OPERATOR_ID, NOW)))
                .assertNext(booking -> {
                    assertThat(booking.businessKey()).isEqualTo(BUSINESS_KEY);
                    assertThat(booking.operatorId()).isEqualTo(OPERATOR_ID);
                    assertThat(booking.status()).isEqualTo(TravelBookingStatus.PENDING);
                })
                .verifyComplete();
    }

    @Test
    void createBookingIsIdempotentAndReturnsTheExistingBooking() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking existing = TravelBooking.create(sagaId, BUSINESS_KEY, OPERATOR_ID, NOW);
        when(this.travelBookingRepository.findByBusinessKey(BUSINESS_KEY)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.createBooking(new CreateBookingRequest(UUID.randomUUID(), BUSINESS_KEY, OPERATOR_ID, NOW)))
                .assertNext(booking -> assertThat(booking).isEqualTo(existing))
                .verifyComplete();

        verify(this.travelBookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingAdvancesFromPendingToConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking pending = TravelBooking.create(sagaId, BUSINESS_KEY, OPERATOR_ID, NOW);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(pending));

        StepVerifier.create(this.service.confirmBooking(new ConfirmBookingRequest(sagaId, NOW)))
                .assertNext(booking -> assertThat(booking.status()).isEqualTo(TravelBookingStatus.CONFIRMED))
                .verifyComplete();
    }

    @Test
    void confirmBookingIsIdempotentWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking confirmed = TravelBooking.create(sagaId, BUSINESS_KEY, OPERATOR_ID, NOW).confirm(NOW);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.confirmBooking(new ConfirmBookingRequest(sagaId, NOW)))
                .assertNext(booking -> assertThat(booking.status()).isEqualTo(TravelBookingStatus.CONFIRMED))
                .verifyComplete();

        verify(this.travelBookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.confirmBooking(new ConfirmBookingRequest(sagaId, NOW)))
                .expectError(BookingNotFoundException.class)
                .verify();
    }

    @Test
    void confirmBookingThrowsWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking cancelled = withStatus(sagaId, TravelBookingStatus.CANCELLED);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.confirmBooking(new ConfirmBookingRequest(sagaId, NOW)))
                .expectError(IllegalTravelBookingTransitionException.class)
                .verify();
    }

    @Test
    void validateCancellableReturnsTheBookingWhenPending() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking pending = TravelBooking.create(sagaId, BUSINESS_KEY, OPERATOR_ID, NOW);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(pending));

        StepVerifier.create(this.service.validateCancellable(new CancelBookingRequest(sagaId, null)))
                .assertNext(booking -> assertThat(booking.status()).isEqualTo(TravelBookingStatus.PENDING))
                .verifyComplete();
    }

    @Test
    void validateCancellableReturnsTheBookingWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking cancelled = withStatus(sagaId, TravelBookingStatus.CANCELLED);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.validateCancellable(new CancelBookingRequest(sagaId, null)))
                .assertNext(booking -> assertThat(booking.status()).isEqualTo(TravelBookingStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void validateCancellableThrowsWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking confirmed = TravelBooking.create(sagaId, BUSINESS_KEY, OPERATOR_ID, NOW).confirm(NOW);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.validateCancellable(new CancelBookingRequest(sagaId, "customer changed mind")))
                .expectError(BookingAlreadyConfirmedException.class)
                .verify();
    }

    @Test
    void validateCancellableThrowsWhenAlreadyFailed() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBooking failed = withStatus(sagaId, TravelBookingStatus.FAILED);
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.just(failed));

        StepVerifier.create(this.service.validateCancellable(new CancelBookingRequest(sagaId, null)))
                .expectError(BookingAlreadyFailedException.class)
                .verify();
    }

    @Test
    void validateCancellableFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.travelBookingRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.validateCancellable(new CancelBookingRequest(sagaId, null)))
                .expectError(BookingNotFoundException.class)
                .verify();
    }

    /**
     * {@link TravelBooking} has no public {@code cancel()}/{@code fail()} transition method yet —
     * {@code validateCancellable} is deliberately read-only (see its own javadoc), and nothing else
     * in this service ever reaches {@code CANCELLED}/{@code FAILED} either. Constructs the row
     * directly via the canonical constructor to exercise those states regardless.
     */
    private static TravelBooking withStatus(final UUID sagaId,
            final TravelBookingStatus status) {
        return new TravelBooking(sagaId, BUSINESS_KEY, OPERATOR_ID, status, NOW, NOW, null);
    }
}
