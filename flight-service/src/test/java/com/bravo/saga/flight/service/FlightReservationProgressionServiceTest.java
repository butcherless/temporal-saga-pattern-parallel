package com.bravo.saga.flight.service;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.flight.domain.FlightReservation;
import com.bravo.saga.flight.persistence.FlightReservationRepository;
import com.bravo.saga.leg.domain.IllegalReservationTransitionException;
import com.bravo.saga.leg.domain.ReservationStatus;
import com.bravo.saga.leg.service.ReserveLegRequest;
import com.bravo.saga.leg.service.SagaStepRequest;
import com.bravo.saga.leg.service.SimulatedTransientReservationFaultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static com.bravo.saga.flight.FlightTestFixtures.NOW;
import static com.bravo.saga.flight.FlightTestFixtures.faultInjectingBooking;
import static com.bravo.saga.flight.FlightTestFixtures.sampleBooking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adapter-free unit test for {@link FlightReservationProgressionService} — {@link FlightReservationRepository} is mocked. */
@ExtendWith(MockitoExtension.class)
class FlightReservationProgressionServiceTest {

    @Mock
    private FlightReservationRepository flightReservationRepository;

    private FlightReservationProgressionService service;

    @BeforeEach
    void setUp() {
        this.service = new FlightReservationProgressionService(this.flightReservationRepository);
        lenient().when(this.flightReservationRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void reserveFlightCreatesANewReservationWhenNoneExistsForTheSagaId() {
        final UUID sagaId = UUID.randomUUID();
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, sampleBooking(), NOW)))
                .assertNext(reservation -> {
                    assertThat(reservation.id()).isEqualTo(sagaId);
                    assertThat(reservation.flightNumber()).isEqualTo("IB6275");
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                })
                .verifyComplete();
    }

    @Test
    void reserveFlightIsIdempotentAndReturnsTheExistingReservation() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation existing = FlightReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, sampleBooking(), NOW)))
                .assertNext(reservation -> assertThat(reservation).isEqualTo(existing))
                .verifyComplete();

        verify(this.flightReservationRepository, never()).save(any());
    }

    @Test
    void reserveFlightSimulatesAOneTimeTransientFaultWhenTheSentinelOfferReferenceIsUsed() {
        final UUID sagaId = UUID.randomUUID();

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, faultInjectingBooking(), NOW)))
                .expectError(SimulatedTransientReservationFaultException.class)
                .verify();

        verify(this.flightReservationRepository, never()).findById(any(UUID.class));
    }

    @Test
    void reserveFlightSucceedsOnRetryAfterTheOneTimeSimulatedTransientFault() {
        final UUID sagaId = UUID.randomUUID();
        final ReserveLegRequest<FlightBookingRequest> request = new ReserveLegRequest<>(sagaId, faultInjectingBooking(), NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.reserve(request))
                .expectError(SimulatedTransientReservationFaultException.class)
                .verify();

        StepVerifier.create(this.service.reserve(request))
                .assertNext(reservation -> {
                    assertThat(reservation.id()).isEqualTo(sagaId);
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                })
                .verifyComplete();
    }

    @Test
    void confirmFlightAdvancesFromReservedToConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation reserved = FlightReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();
    }

    @Test
    void confirmFlightIsIdempotentWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation confirmed = FlightReservation.reserve(sagaId, sampleBooking(), NOW).confirm(NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();

        verify(this.flightReservationRepository, never()).save(any());
    }

    @Test
    void confirmFlightFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void confirmFlightThrowsWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation cancelled = FlightReservation.reserve(sagaId, sampleBooking(), NOW).cancel(NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }

    @Test
    void cancelFlightReservationAdvancesFromReservedToCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation reserved = FlightReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void cancelFlightReservationIsIdempotentWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation cancelled = FlightReservation.reserve(sagaId, sampleBooking(), NOW).cancel(NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();

        verify(this.flightReservationRepository, never()).save(any());
    }

    @Test
    void cancelFlightReservationFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void cancelFlightReservationThrowsWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final FlightReservation confirmed = FlightReservation.reserve(sagaId, sampleBooking(), NOW).confirm(NOW);
        when(this.flightReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }
}
