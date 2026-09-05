package com.bravo.saga.hotel.service;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.hotel.domain.HotelReservation;
import com.bravo.saga.hotel.persistence.HotelReservationRepository;
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

import static com.bravo.saga.hotel.HotelTestFixtures.NOW;
import static com.bravo.saga.hotel.HotelTestFixtures.faultInjectingBooking;
import static com.bravo.saga.hotel.HotelTestFixtures.sampleBooking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adapter-free unit test for {@link HotelReservationProgressionService} — {@link HotelReservationRepository} is mocked. */
@ExtendWith(MockitoExtension.class)
class HotelReservationProgressionServiceTest {

    @Mock
    private HotelReservationRepository hotelReservationRepository;

    private HotelReservationProgressionService service;

    @BeforeEach
    void setUp() {
        this.service = new HotelReservationProgressionService(this.hotelReservationRepository);
        lenient().when(this.hotelReservationRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void reserveHotelCreatesANewReservationWhenNoneExistsForTheSagaId() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, sampleBooking(), NOW)))
                .assertNext(reservation -> {
                    assertThat(reservation.id()).isEqualTo(sagaId);
                    assertThat(reservation.hotelCode()).isEqualTo("HTL-PARIS-014");
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                })
                .verifyComplete();
    }

    @Test
    void reserveHotelIsIdempotentAndReturnsTheExistingReservation() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation existing = HotelReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, sampleBooking(), NOW)))
                .assertNext(reservation -> assertThat(reservation).isEqualTo(existing))
                .verifyComplete();

        verify(this.hotelReservationRepository, never()).save(any());
    }

    @Test
    void reserveHotelSimulatesAOneTimeTransientFaultWhenTheSentinelOfferReferenceIsUsed() {
        final UUID sagaId = UUID.randomUUID();

        StepVerifier.create(this.service.reserve(new ReserveLegRequest<>(sagaId, faultInjectingBooking(), NOW)))
                .expectError(SimulatedTransientReservationFaultException.class)
                .verify();

        verify(this.hotelReservationRepository, never()).findById(any(UUID.class));
    }

    @Test
    void reserveHotelSucceedsOnRetryAfterTheOneTimeSimulatedTransientFault() {
        final UUID sagaId = UUID.randomUUID();
        final ReserveLegRequest<HotelBookingRequest> request = new ReserveLegRequest<>(sagaId, faultInjectingBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

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
    void confirmHotelAdvancesFromReservedToConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation reserved = HotelReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();
    }

    @Test
    void confirmHotelIsIdempotentWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation confirmed = HotelReservation.reserve(sagaId, sampleBooking(), NOW).confirm(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();

        verify(this.hotelReservationRepository, never()).save(any());
    }

    @Test
    void confirmHotelFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void confirmHotelThrowsWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation cancelled = HotelReservation.reserve(sagaId, sampleBooking(), NOW).cancel(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.confirm(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }

    @Test
    void cancelHotelReservationAdvancesFromReservedToCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation reserved = HotelReservation.reserve(sagaId, sampleBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void cancelHotelReservationIsIdempotentWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation cancelled = HotelReservation.reserve(sagaId, sampleBooking(), NOW).cancel(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();

        verify(this.hotelReservationRepository, never()).save(any());
    }

    @Test
    void cancelHotelReservationFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void cancelHotelReservationThrowsWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation confirmed = HotelReservation.reserve(sagaId, sampleBooking(), NOW).confirm(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.cancel(new SagaStepRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }
}
