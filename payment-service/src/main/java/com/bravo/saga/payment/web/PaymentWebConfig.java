package com.bravo.saga.payment.web;

import com.bravo.saga.payment.persistence.PaymentHoldRepository;
import com.bravo.saga.payment.service.PaymentHoldProgressionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires {@link PaymentHoldProgressionService} for {@link PaymentController}. */
@Configuration
public class PaymentWebConfig {

    @Bean
    public PaymentHoldProgressionService paymentHoldProgressionService(final PaymentHoldRepository paymentHoldRepository) {
        return new PaymentHoldProgressionService(paymentHoldRepository);
    }
}
