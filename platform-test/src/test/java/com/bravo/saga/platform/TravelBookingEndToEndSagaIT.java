package com.bravo.saga.platform;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Testcontainers-Postgres + Temporal dev server IT — the Temporal analogue of
 * {@code temporal-saga-pattern}'s {@code TemporalEndToEndSagaIT}, adapted for this repo's
 * three-way parallel fan-out instead of a dependent chain. Boots all six services as real,
 * separate forked {@code java -jar} processes (same reason as the sibling repo: each service's own
 * {@code db/migration}/{@code application.yml} live under identical classpath paths inside its own
 * jar, so running them in-process on one shared classpath would make Flyway/Spring Boot's config
 * loading see all six combined instead of each service's own).
 *
 * <p>Drives one full saga via {@code POST /travel-bookings} on {@code booking-service}, then
 * asserts on <b>both</b> the Workflow Execution itself (checked via a {@link WorkflowClient}
 * pointed at the same dev server) <b>and</b> the business outcome, read directly out of each
 * service's own Postgres database: {@code booking_db.travel_booking.status = CONFIRMED},
 * {@code flight_db.flight_reservation.status}/{@code hotel_db.hotel_reservation.status}/
 * {@code car_db.car_reservation.status = CONFIRMED}, and
 * {@code payment_db.payment_hold.status = CAPTURED} — proof that every layer, from the controller
 * down through the service/persistence layers of all five business services, actually reads and
 * writes real rows for the main use case (docs/travel-booking/use-case-main.md, steps 1-8).
 */
@Testcontainers(disabledWithoutDocker = true)
class TravelBookingEndToEndSagaIT {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration SAGA_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private static final int ORCHESTRATOR_PORT = 8090;
    private static final int BOOKING_SERVICE_PORT = 8091;
    private static final int FLIGHT_SERVICE_PORT = 8092;
    private static final int HOTEL_SERVICE_PORT = 8093;
    private static final int CAR_SERVICE_PORT = 8094;
    private static final int PAYMENT_SERVICE_PORT = 8095;
    private static final int TEMPORAL_GRPC_PORT = 7233;

    /**
     * Default {@code max_connections=100} is exhausted once five business services' R2DBC+Hikari
     * pools share one instance (same reasoning as the project's own {@code docker-compose.yml},
     * which bumps this for the same reason) — raised here to match.
     */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withCommand("postgres", "-c", "max_connections=300")
            .withEnv("POSTGRES_MULTIPLE_DATABASES", "booking_db,flight_db,hotel_db,car_db,payment_db")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("init-multiple-databases.sh"),
                    "/docker-entrypoint-initdb.d/init-multiple-databases.sh");

    @Container
    static final GenericContainer<?> TEMPORAL = new GenericContainer<>("temporalio/temporal:1.8.2")
            .withCommand("server", "start-dev", "--ip", "0.0.0.0")
            .withExposedPorts(TEMPORAL_GRPC_PORT);

    private static List<Process> processes;

    @BeforeAll
    static void startAllServices() throws IOException, InterruptedException {
        final Path logDir = Path.of("target", "e2e-logs");
        Files.createDirectories(logDir);

        processes = List.of(
                startOrchestrator(logDir),
                startBusinessService("booking-service", logDir, true),
                startBusinessService("flight-service", logDir, false),
                startBusinessService("hotel-service", logDir, false),
                startBusinessService("car-service", logDir, false),
                startBusinessService("payment-service", logDir, false));

        awaitHealthy(ORCHESTRATOR_PORT);
        awaitHealthy(BOOKING_SERVICE_PORT);
        awaitHealthy(FLIGHT_SERVICE_PORT);
        awaitHealthy(HOTEL_SERVICE_PORT);
        awaitHealthy(CAR_SERVICE_PORT);
        awaitHealthy(PAYMENT_SERVICE_PORT);
    }

    @AfterAll
    static void stopAllServices() {
        processes.forEach(Process::destroyForcibly);
    }

    @Test
    void postTravelBookingsDrivesTheFullHappyPathSagaToConfirmed() throws Exception {
        final String businessKey = "TRIP-2026-E2E-%s".formatted(UUID.randomUUID());
        final UUID sagaId = this.postTravelBooking(businessKey);

        awaitWorkflowCompleted(businessKey);
        awaitBookingStatus(sagaId, "CONFIRMED");
        assertReservationStatus("flight_db", "flight_reservation", sagaId, "CONFIRMED");
        assertReservationStatus("hotel_db", "hotel_reservation", sagaId, "CONFIRMED");
        assertReservationStatus("car_db", "car_reservation", sagaId, "CONFIRMED");
        assertPaymentHoldStatus(sagaId, "CAPTURED");
    }

    /**
     * A repeat {@code POST /travel-bookings} for the same businessKey is idempotent —
     * {@code BookingCreationHandler} finds the booking the first call already created and skips
     * starting a second Workflow Execution, so only one saga ever runs to completion.
     */
    @Test
    void postTravelBookingsIsIdempotentOnADuplicateBusinessKey() throws Exception {
        final String businessKey = "TRIP-2026-E2E-%s".formatted(UUID.randomUUID());

        final int firstStatus = this.postTravelBookingRaw(businessKey);
        final int secondStatus = this.postTravelBookingRaw(businessKey);
        assertThat(firstStatus).isEqualTo(202);
        assertThat(secondStatus).isIn(200, 202);

        final UUID sagaId = loadSagaId(businessKey);
        awaitWorkflowCompleted(businessKey);
        awaitBookingStatus(sagaId, "CONFIRMED");
    }

    private UUID postTravelBooking(final String businessKey) throws Exception {
        assertThat(this.postTravelBookingRaw(businessKey)).isEqualTo(202);
        return loadSagaId(businessKey);
    }

    private int postTravelBookingRaw(final String businessKey) throws Exception {
        final String requestBody = """
                {
                  "operatorId": "agency-042",
                  "paymentAuthorizationToken": "pat_9f3e2b7c8a1d4e5f",
                  "businessKey": "%s",
                  "flight": {
                    "flightNumber": "IB6275",
                    "originAirportCode": "MAD",
                    "destinationAirportCode": "CDG",
                    "departureDate": "2026-09-10",
                    "passengerCount": 2,
                    "offerReference": "%s"
                  },
                  "hotel": {
                    "hotelCode": "HTL-PARIS-014",
                    "roomType": "DOUBLE_DELUXE",
                    "checkInDate": "2026-09-10",
                    "checkOutDate": "2026-09-14",
                    "guestCount": 2,
                    "offerReference": "%s"
                  },
                  "car": {
                    "carCategory": "COMPACT",
                    "pickupLocation": "CDG",
                    "pickupDate": "2026-09-10",
                    "dropoffDate": "2026-09-14",
                    "offerReference": "%s"
                  }
                }""".formatted(businessKey, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        final HttpClient client = HttpClient.newHttpClient();
        final HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:%d/travel-bookings".formatted(BOOKING_SERVICE_PORT)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        return response.statusCode();
    }

    private static Process startOrchestrator(final Path logDir) throws IOException {
        final ProcessBuilder builder = execJarProcessBuilder("saga-orchestrator-temporal", logDir);
        builder.environment().put("TEMPORAL_TARGET", temporalTarget());
        builder.environment().put("FLIGHT_SERVICE_URL", "http://localhost:%d".formatted(FLIGHT_SERVICE_PORT));
        builder.environment().put("HOTEL_SERVICE_URL", "http://localhost:%d".formatted(HOTEL_SERVICE_PORT));
        builder.environment().put("CAR_SERVICE_URL", "http://localhost:%d".formatted(CAR_SERVICE_PORT));
        builder.environment().put("PAYMENT_SERVICE_URL", "http://localhost:%d".formatted(PAYMENT_SERVICE_PORT));
        builder.environment().put("BOOKING_SERVICE_URL", "http://localhost:%d".formatted(BOOKING_SERVICE_PORT));
        return builder.start();
    }

    private static Process startBusinessService(final String module,
            final Path logDir,
            final boolean startsWorkflows) throws IOException {
        final ProcessBuilder builder = execJarProcessBuilder(module, logDir);
        builder.environment().put("DB_HOST", POSTGRES.getHost());
        builder.environment().put("DB_PORT", String.valueOf(POSTGRES.getMappedPort(5432)));
        builder.environment().put("DB_USERNAME", POSTGRES.getUsername());
        builder.environment().put("DB_PASSWORD", POSTGRES.getPassword());
        if (startsWorkflows) {
            builder.environment().put("TEMPORAL_TARGET", temporalTarget());
        }
        return builder.start();
    }

    private static ProcessBuilder execJarProcessBuilder(final String module,
            final Path logDir) {
        final String version = System.getProperty("saga.platform.version");
        final Path jar = Path.of("..", module, "target", module + "-" + version + "-exec.jar").toAbsolutePath().normalize();
        final ProcessBuilder builder = new ProcessBuilder("java", "-jar", jar.toString());
        builder.redirectOutput(logDir.resolve(module + ".log").toFile());
        builder.redirectErrorStream(true);
        return builder;
    }

    private static String temporalTarget() {
        return TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(TEMPORAL_GRPC_PORT);
    }

    private static void awaitHealthy(final int port) throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newHttpClient();
        final Instant deadline = Instant.now().plus(HEALTH_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            try {
                final HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:%d/actuator/health".formatted(port))).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"UP\"")) {
                    return;
                }
            } catch (final IOException _) {
                // Not up yet — keep polling until the deadline.
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        throw new IllegalStateException("Service on port %d did not become healthy within %s".formatted(port, HEALTH_TIMEOUT));
    }

    private static void awaitWorkflowCompleted(final String businessKey) {
        final WorkflowServiceStubs serviceStubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(temporalTarget()).build());
        try {
            final WorkflowClient workflowClient = WorkflowClient.newInstance(serviceStubs);
            final WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(businessKey);
            workflowStub.getResult(SAGA_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS, Void.class);
        } catch (final Exception error) {
            throw new AssertionError(
                    "Travel-booking saga Workflow for businessKey %s did not complete within %s".formatted(businessKey, SAGA_TIMEOUT), error);
        } finally {
            serviceStubs.shutdownNow();
        }
    }

    private static UUID loadSagaId(final String businessKey) throws SQLException {
        return queryOne("booking_db", "SELECT id FROM travel_booking WHERE business_key = ?", businessKey,
                resultSet -> (UUID) resultSet.getObject("id"));
    }

    private static void assertReservationStatus(final String database,
            final String table,
            final UUID sagaId,
            final String expectedStatus) throws SQLException {
        final String status = queryOne(database, "SELECT status FROM %s WHERE id = ?".formatted(table), sagaId,
                resultSet -> resultSet.getString("status"));
        assertThat(status).isEqualTo(expectedStatus);
    }

    private static void assertPaymentHoldStatus(final UUID sagaId,
            final String expectedStatus) throws SQLException {
        final String status = queryOne("payment_db", "SELECT status FROM payment_hold WHERE id = ?", sagaId,
                resultSet -> resultSet.getString("status"));
        assertThat(status).isEqualTo(expectedStatus);
    }

    private static void awaitBookingStatus(final UUID sagaId,
            final String expectedStatus) throws Exception {
        final Instant deadline = Instant.now().plus(SAGA_TIMEOUT);
        String lastSeenStatus = null;

        while (Instant.now().isBefore(deadline)) {
            lastSeenStatus = queryOne("booking_db", "SELECT status FROM travel_booking WHERE id = ?", sagaId,
                    resultSet -> resultSet.getString("status"));
            if (expectedStatus.equals(lastSeenStatus)) {
                return;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        throw new AssertionError("Booking %s did not reach %s within %s (last seen status: %s)"
                .formatted(sagaId, expectedStatus, SAGA_TIMEOUT, lastSeenStatus));
    }

    /**
     * Shared single-row/single-column query plumbing behind {@code loadSagaId}/{@code assert*Status}/
     * {@code awaitBookingStatus} — each of the five Testcontainers-Postgres databases only ever
     * needs a one-parameter {@code SELECT} by id or business key, so the connection/statement/
     * result-set lifecycle lives here once instead of once per accessor.
     */
    private static <T> T queryOne(final String database,
            final String sql,
            final Object param,
            final ResultSetExtractor<T> extractor)
            throws SQLException {
        final String jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, param);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Query returned no row: %s".formatted(sql));
                }
                return extractor.extract(resultSet);
            }
        }
    }

    @FunctionalInterface
    private interface ResultSetExtractor<T> {
        T extract(ResultSet resultSet) throws SQLException;
    }
}
