package com.bravo.saga.orchestrator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * One {@link WebClient} per business service, each pointed at that service's own base URL —
 * disambiguated by bean name, same convention {@code temporal-saga-pattern}'s own
 * {@code ActivityWebClientConfig} uses. Base URLs come from {@link ServicesProperties}. Built
 * from the injected, auto-configured {@link WebClient.Builder} so every
 * {@code WebClientCustomizer} (tracing/metrics instrumentation among them) still applies —
 * {@link WebClient#create(String)} would bypass that entirely. Connect/read timeouts come from
 * {@code spring.http.clients.*} in {@code application.yml}.
 */
@Configuration
@EnableConfigurationProperties(ServicesProperties.class)
public class ActivityWebClientConfig {

    @Bean
    public WebClient flightWebClient(final WebClient.Builder webClientBuilder,
            final ServicesProperties servicesProperties) {
        return webClientBuilder.baseUrl(servicesProperties.flight().baseUrl()).build();
    }

    @Bean
    public WebClient hotelWebClient(final WebClient.Builder webClientBuilder,
            final ServicesProperties servicesProperties) {
        return webClientBuilder.baseUrl(servicesProperties.hotel().baseUrl()).build();
    }

    @Bean
    public WebClient carWebClient(final WebClient.Builder webClientBuilder,
            final ServicesProperties servicesProperties) {
        return webClientBuilder.baseUrl(servicesProperties.car().baseUrl()).build();
    }

    @Bean
    public WebClient paymentWebClient(final WebClient.Builder webClientBuilder,
            final ServicesProperties servicesProperties) {
        return webClientBuilder.baseUrl(servicesProperties.payment().baseUrl()).build();
    }

    @Bean
    public WebClient bookingWebClient(final WebClient.Builder webClientBuilder,
            final ServicesProperties servicesProperties) {
        return webClientBuilder.baseUrl(servicesProperties.booking().baseUrl()).build();
    }
}
