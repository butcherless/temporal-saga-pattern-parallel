package com.bravo.saga.orchestrator.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * One {@link WebClient} per business service, each pointed at that service's own base URL —
 * disambiguated by bean name, same convention {@code temporal-saga-pattern}'s own
 * {@code ActivityWebClientConfig} uses. Base URLs come from {@link ServicesProperties}. Built
 * from the injected, auto-configured {@link WebClient.Builder} so every
 * {@code WebClientCustomizer} (tracing/metrics instrumentation among them) still applies —
 * {@link WebClient#create(String)} would bypass that entirely.
 */
@Configuration
@EnableConfigurationProperties(ServicesProperties.class)
public class ActivityWebClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    /**
     * {@code spring.http.clients.connect-timeout}/{@code read-timeout} only reach the blocking
     * {@code RestClient} in Spring Boot 4.1 — {@code WebClientAutoConfiguration} builds the
     * reactive {@link WebClient.Builder} bean with no {@code HttpClientSettings} applied at all,
     * so those properties are silently a no-op for every {@link WebClient} built here. A
     * {@link ClientHttpConnector} wired directly onto Reactor Netty's {@link HttpClient} is the
     * only way to actually bound connect/read time for these calls — read-timeout is set well
     * above the leg services' simulated processing delay (10-20s) and under
     * {@code TravelBookingSagaWorkflowImpl}'s own Activity {@code StartToCloseTimeout} (30s), so a
     * genuinely stalled response fails at the HTTP client first with a clear cause.
     */
    @Bean
    public WebClientCustomizer activityWebClientTimeoutCustomizer() {
        final HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS)));
        final ClientHttpConnector clientHttpConnector = new ReactorClientHttpConnector(httpClient);
        return builder -> builder.clientConnector(clientHttpConnector);
    }

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
