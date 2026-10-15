package com.classroomgrouping.platform;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.grouping.api.DueWork;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import com.jayway.jsonpath.JsonPath;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated recovery evidence; the durable probe is never included in the application jar. */
class FoundationRecoveryTest {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));
    private static final boolean NATIVE = Boolean.getBoolean("foundation.test.native");
    private static String databaseUrl;
    private static String databaseUser;
    private static String databasePassword;

    @BeforeAll static void database() throws java.io.IOException {
        if (NATIVE) {
            // Explicit supplementary mode for a disposable real PostgreSQL instance; never skip tests.
            databaseUrl = required("foundation.test.jdbc.url");
            databaseUser = required("foundation.test.jdbc.user");
            databasePassword = required("foundation.test.jdbc.password");
            required("foundation.test.pgctl");
            required("foundation.test.pgdata");
            var address = URI.create(databaseUrl.substring("jdbc:".length()));
            if (!("127.0.0.1".equals(address.getHost()) || "localhost".equals(address.getHost()))
                    || address.getPort() < 1
                    || !java.nio.file.Path.of(required("foundation.test.pgdata")).isAbsolute()) {
                throw new IllegalArgumentException("Native verification requires loopback, an explicit port, and an absolute data path");
            }
        } else {
            // Docker may reassign a randomly published host port on container restart.
            // Keep the endpoint stable so the running application's pool must recover
            // from a real database/container restart, rather than a changed test URL.
            int recoveryPort;
            try (var available = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
                recoveryPort = available.getLocalPort();
            }
            POSTGRES.setPortBindings(java.util.List.of("127.0.0.1:" + recoveryPort + ":5432"));
            POSTGRES.start();
            databaseUrl = POSTGRES.getJdbcUrl();
            databaseUser = POSTGRES.getUsername();
            databasePassword = POSTGRES.getPassword();
        }
    }

    @AfterAll static void cleanup() { if (!NATIVE) POSTGRES.stop(); }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private ConfigurableApplicationContext start(boolean scheduler, boolean local) {
        return new SpringApplicationBuilder(ClassroomGroupingApplication.class,
                FoundationIntegrationTest.ProbeConfiguration.class).run(
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.profiles.active=" + (local ? "local" : "foundation-test"),
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + databaseUser,
                "--spring.datasource.password=" + databasePassword,
                "--app.demo.enabled=true", "--app.demo.username=foundation-recovery",
                "--app.demo.password=foundation-recovery-password",
                "--app.scheduler.enabled=" + scheduler, "--app.scheduler.interval-ms=60000");
    }

    private URI uri(ConfigurableApplicationContext app, String path) {
        return URI.create("http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort() + path);
    }

    @Test void startupCatchesUpDurableWorkMissedWhileApplicationWasStopped() {
        UUID id = UUID.randomUUID();
        try (var first = start(false, true)) {
            first.getBean(JdbcTemplate.class).update(
                    "INSERT INTO test_deadline_probe(id, deadline) VALUES (?, clock_timestamp() - interval '1 second')", id);
            assertEquals(0, first.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT executions FROM test_deadline_probe WHERE id=?", Integer.class, id));
        }
        // No browser traffic or in-memory task survives this shutdown.
        try (var restarted = start(true, true)) {
            assertEquals(1, restarted.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT executions FROM test_deadline_probe WHERE id=?", Integer.class, id));
            restarted.getBean(DueWork.class).processDueWork();
            assertEquals(1, restarted.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT executions FROM test_deadline_probe WHERE id=?", Integer.class, id));
        }
    }

    @Test void authenticatedSessionRecoversAfterDatabaseRestart() throws Exception {
        try (var app = start(false, true)) {
            var client = HttpClient.newBuilder()
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .connectTimeout(Duration.ofSeconds(5)).build();
            var csrf = client.send(HttpRequest.newBuilder(uri(app, "/api/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            String token = JsonPath.read(csrf.body(), "$.token");
            var login = client.send(HttpRequest.newBuilder(uri(app, "/api/auth/login"))
                    .header("X-CSRF-TOKEN", token).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "username=foundation-recovery&password=foundation-recovery-password")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, login.statusCode());
            if (NATIVE) {
                var restart = new ProcessBuilder(required("foundation.test.pgctl"),
                        "-D", required("foundation.test.pgdata"), "-l",
                        java.nio.file.Path.of(required("foundation.test.pgdata")).getParent()
                                .resolve("postgres.log").toString(),
                        "-m", "fast", "-w", "restart", "-o", "-h 127.0.0.1 -p "
                                + URI.create(databaseUrl.substring("jdbc:".length())).getPort())
                        .redirectErrorStream(true)
                        .redirectOutput(java.nio.file.Path.of(required("foundation.test.pgdata")).getParent()
                                .resolve("pgctl-restart.log").toFile()).start();
                assertTrue(restart.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, restart.exitValue());
            } else {
                DockerClientFactory.instance().client().restartContainerCmd(POSTGRES.getContainerId()).exec();
                var binding = DockerClientFactory.instance().client().inspectContainerCmd(POSTGRES.getContainerId())
                        .exec().getNetworkSettings().getPorts().getBindings()
                        .get(com.github.dockerjava.api.model.ExposedPort.tcp(5432));
                assertNotNull(binding);
                assertEquals(URI.create(databaseUrl.substring("jdbc:".length())).getPort(),
                        Integer.parseInt(binding[0].getHostPortSpec()));
            }
            await().atMost(Duration.ofSeconds(40)).ignoreExceptions().untilAsserted(() -> {
                var session = client.send(HttpRequest.newBuilder(uri(app, "/api/auth/session"))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, session.statusCode());
                assertEquals("foundation-recovery", JsonPath.read(session.body(), "$.username"));
            });
        }
    }

    @Test void productionDefaultsIssueSecureHttpOnlyHostOnlyCookies() throws Exception {
        try (var app = start(false, false)) {
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(uri(app, "/api/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String cookie = response.headers().firstValue("set-cookie").orElseThrow();
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("SameSite=Lax"));
            assertFalse(cookie.toLowerCase(java.util.Locale.ROOT).contains("domain="));
        }
    }
}
