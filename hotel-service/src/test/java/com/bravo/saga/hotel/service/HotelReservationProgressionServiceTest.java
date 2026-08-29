package com.bravo.saga.hotel.service;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.hotel.domain.HotelReservation;
import com.bravo.saga.hotel.domain.IllegalReservationTransitionException;
import com.bravo.saga.hotel.domain.ReservationStatus;
import com.bravo.saga.hotel.persistence.HotelReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adapter-free unit test for {@link HotelReservationProgressionService} — {@link HotelReservationRepository} is mocked. */
@ExtendWith(MockitoExtension.class)
class HotelReservationProgressionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

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

        StepVerifier.create(this.service.reserveHotel(new ReserveHotelRequest(sagaId, hotelBooking(), NOW)))
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
        final HotelReservation existing = HotelReservation.reserve(sagaId, hotelBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.reserveHotel(new ReserveHotelRequest(sagaId, hotelBooking(), NOW)))
                .assertNext(reservation -> assertThat(reservation).isEqualTo(existing))
                .verifyComplete();

        verify(this.hotelReservationRepository, never()).save(any());
    }

    @Test
    void reserveHotelSimulatesAOneTimeTransientFaultWhenTheSentinelOfferReferenceIsUsed() {
        final UUID sagaId = UUID.randomUUID();

        StepVerifier.create(this.service.reserveHotel(new ReserveHotelRequest(sagaId, faultInjectingHotelBooking(), NOW)))
                .expectError(SimulatedTransientReservationFaultException.class)
                .verify();

        verify(this.hotelReservationRepository, never()).findById(any(UUID.class));
    }

    @Test
    void reserveHotelSucceedsOnRetryAfterTheOneTimeSimulatedTransientFault() {
        final UUID sagaId = UUID.randomUUID();
        final ReserveHotelRequest request = new ReserveHotelRequest(sagaId, faultInjectingHotelBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.reserveHotel(request))
                .expectError(SimulatedTransientReservationFaultException.class)
                .verify();

        StepVerifier.create(this.service.reserveHotel(request))
                .assertNext(reservation -> {
                    assertThat(reservation.id()).isEqualTo(sagaId);
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                })
                .verifyComplete();
    }

    @Test
    void confirmHotelAdvancesFromReservedToConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation reserved = HotelReservation.reserve(sagaId, hotelBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.confirmHotel(new ConfirmHotelRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();
    }

    @Test
    void confirmHotelIsIdempotentWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation confirmed = HotelReservation.reserve(sagaId, hotelBooking(), NOW).confirm(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.confirmHotel(new ConfirmHotelRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();

        verify(this.hotelReservationRepository, never()).save(any());
    }

    @Test
    void confirmHotelFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.confirmHotel(new ConfirmHotelRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void confirmHotelThrowsWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation cancelled = HotelReservation.reserve(sagaId, hotelBooking(), NOW).cancel(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.confirmHotel(new ConfirmHotelRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }

    @Test
    void cancelHotelReservationAdvancesFromReservedToCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation reserved = HotelReservation.reserve(sagaId, hotelBooking(), NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(reserved));

        StepVerifier.create(this.service.cancelHotelReservation(new CancelHotelReservationRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void cancelHotelReservationIsIdempotentWhenAlreadyCancelled() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation cancelled = HotelReservation.reserve(sagaId, hotelBooking(), NOW).cancel(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(this.service.cancelHotelReservation(new CancelHotelReservationRequest(sagaId, NOW)))
                .assertNext(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();

        verify(this.hotelReservationRepository, never()).save(any());
    }

    @Test
    void cancelHotelReservationFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.cancelHotelReservation(new CancelHotelReservationRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void cancelHotelReservationThrowsWhenAlreadyConfirmed() {
        final UUID sagaId = UUID.randomUUID();
        final HotelReservation confirmed = HotelReservation.reserve(sagaId, hotelBooking(), NOW).confirm(NOW);
        when(this.hotelReservationRepository.findById(sagaId)).thenReturn(Mono.just(confirmed));

        StepVerifier.create(this.service.cancelHotelReservation(new CancelHotelReservationRequest(sagaId, NOW)))
                .expectError(IllegalReservationTransitionException.class)
                .verify();
    }

    private static HotelBookingRequest hotelBooking() {
        return new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID());
    }

    private static HotelBookingRequest faultInjectingHotelBooking() {
        return new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2,
                HotelReservationProgressionService.SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE);
    }
}
