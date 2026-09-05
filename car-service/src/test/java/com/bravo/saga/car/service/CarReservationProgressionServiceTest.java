package com.bravo.saga.car.service;

import com.bravo.saga.car.domain.CarReservation;
import com.bravo.saga.car.persistence.CarReservationRepository;
import com.bravo.saga.common.workflow.CarBookingRequest;
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

import static com.bravo.saga.car.CarTestFixtures.NOW;
import static com.bravo.saga.car.CarTestFixtures.faultInjectingBooking;
import static com.bravo.saga.car.CarTestFixtures.sampleBooking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adapter-free unit test for {@link CarReservationProgressionService} — {@link CarReservationRepository} is mocked. */
@ExtendWith(MockitoExtension.class)
class CarReservationProgressionServiceTest {

    @Mock
    private CarReservationRepository carReservationRepository;

    private CarReservationProgressionService service;

    @BeforeEach
    void setUp() {
        this.service = new CarReservationProgressionService(this.carReservationRepository);
        lenient().when(this.carReservationRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void reserveCarCreatesANewReservationWhenNoneExistsForTheSagaId() {
        final UUID sagaId = UUID.randomUUID();
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, sampleBooking(), NOW)))
                .assertNext(reservation -> {
                    assertThat(reservation.id()).isEqualTo(sagaId);
                    assertThat(reservation.carCategory()).isEqualTo("COMPACT");
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                })
                .verifyComplete();
    }

    @Test
    void reserveCarIsIdempotentAndReturnsTheExistingReservation() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation existing = CarReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, sampleBooking(), NOW)))
                .assertNext(reservation -> assertThat(reservation).isEqualTo(existing))
                .verifyComplete();

        verify(this.carReservationRepository, never()).save(any());
    }

    @Test
    void reserveCarSimulatesAOneTimeTransientFaultWhenTheSentinelOfferReferenceIsUsed() {
        final UUID sagaId = UUID.randomUUID();

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, faultInjectingBooking(), NOW)))
                .expectError(SimulatedTransientReservationFaultException.class)
                .verify();

        verify(this.carReservationRepository, never()).findById(any(UUID.class));
    }

    @Test
    void reserveCarSucceedsOnRetryAfterTheOneTimeSimulatedTransientFault() {
        final UUID sagaId = UUID.randomUUID();
        final ReserveLegRequest<CarBookingRequest> request = new ReserveLegRequest<>(sagaId, faultInjectingBooking(), NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

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
    void confirmCarAdvancesFromReservedToConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation reserved = CarReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();
    }

    @Test
    void confirmCarIsIdempotentWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation confirmed = CarReservation.reserve(sagaId, sampleBooking(), NOW).confirm(NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();

        verify(this.carReservationRepository, never()).save(any());
    }

    @Test
    void confirmCarFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void confirmCarThrowsWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation cancelled = CarReservation.reserve(sagaId, sampleBooking(), NOW).cancel(NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }

    @Test
    void cancelCarReservationAdvancesFromReservedToCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation reserved = CarReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void cancelCarReservationIsIdempotentWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation cancelled = CarReservation.reserve(sagaId, sampleBooking(), NOW).cancel(NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();

        verify(this.carReservationRepository, never()).save(any());
    }

    @Test
    void cancelCarReservationFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void cancelCarReservationThrowsWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final CarReservation confirmed = CarReservation.reserve(sagaId, sampleBooking(), NOW).confirm(NOW);
        when(this.carReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }
}
