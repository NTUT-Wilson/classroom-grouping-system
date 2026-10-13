package com.classroomgrouping.platform;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.grouping.api.DueWork;
import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "app.demo.enabled=true",
                "app.demo.username=foundation-test", "app.demo.password=foundation-test-password",
                "app.scheduler.secret=foundation-scheduler-test-secret-32-plus",
                "app.scheduler.enabled=true", "app.scheduler.interval-ms=100"})
@Import(FoundationIntegrationTest.ProbeConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @AfterAll
    static void stopProbeBeforeContainerCleanup(@Autowired TaskScheduler scheduler) {
        if (scheduler instanceof ThreadPoolTaskScheduler pool) pool.shutdown();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded");
        if (token != null) builder.header("X-CSRF-TOKEN", token);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String csrf(HttpClient client) throws Exception {
        var response = get(client, "/api/auth/csrf");
        assertEquals(200, response.statusCode());
        return JsonPath.read(response.body(), "$.token");
    }
    private void login(HttpClient client) throws Exception {
        assertEquals(204, post(client, "/api/auth/login", csrf(client),
                "username=foundation-test&password=foundation-test-password").statusCode());
    }

    @Test void migrationsAndPersistentSessionSchemaUseRealPostgres() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT password_hash FROM app_account WHERE username='foundation-test'",
                String.class).startsWith("{bcrypt}"));
        assertEquals("spring_session", jdbc.queryForObject("SELECT to_regclass('spring_session')::text", String.class));
    }

    @Test void healthIsMinimalAndProtectedResourcesRejectAnonymousRequests() throws Exception {
        var client = client();
        var health = get(client, "/actuator/health");
        assertEquals(200, health.statusCode());
        assertFalse(health.body().contains("components"));
        var denied = get(client, "/api/auth/session");
        assertEquals(401, denied.statusCode());
        assertTrue(denied.headers().firstValue("X-Correlation-ID").isPresent());
        assertTrue(denied.headers().firstValue("content-type").orElse("").contains("application/problem+json"));
        assertEquals(401, get(client, "/actuator/env").statusCode());
    }

    @Test void loginAndLogoutRequireCsrfAndRotateSessionState() throws Exception {
        var client = client();
        assertEquals(403, post(client, "/api/auth/login", null,
                "username=foundation-test&password=foundation-test-password").statusCode());
        String before = csrf(client);
        String beforeId = ((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore()
                .getCookies().stream().filter(value -> value.getName().equals("SESSION")).findFirst().orElseThrow().getValue();
        var login = post(client, "/api/auth/login", before,
                "username=foundation-test&password=foundation-test-password");
        assertEquals(204, login.statusCode());
        String cookie = login.headers().firstValue("set-cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        String afterId = ((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore()
                .getCookies().stream().filter(value -> value.getName().equals("SESSION")).findFirst().orElseThrow().getValue();
        assertNotEquals(beforeId, afterId);
        assertEquals(200, get(client, "/api/auth/session").statusCode());
        assertEquals(403, get(client, "/actuator/env").statusCode());
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name='foundation-test'",
                Integer.class) > 0);
        assertEquals(403, post(client, "/api/auth/logout", before, "").statusCode());
        assertEquals(204, post(client, "/api/auth/logout", csrf(client), "").statusCode());
        assertEquals(401, get(client, "/api/auth/session").statusCode());
    }

    @Test void incorrectCredentialsDoNotAuthenticate() throws Exception {
        var client = client();
        assertEquals(401, post(client, "/api/auth/login", csrf(client),
                "username=foundation-test&password=incorrect").statusCode());
        assertEquals(401, get(client, "/api/auth/session").statusCode());
    }

    @Test void expiredJdbcSessionsRequireAuthenticationAgain() throws Exception {
        var client = client();
        login(client);
        jdbc.update("UPDATE spring_session SET last_access_time=0, expiry_time=0 WHERE principal_name='foundation-test'");
        assertEquals(401, get(client, "/api/auth/session").statusCode());
        assertNotNull(csrf(client));
    }

    @Test void browserSessionCannotInvokeMachineScheduler() throws Exception {
        var client = client();
        login(client);
        assertEquals(401, post(client, "/internal/scheduler/tick", csrf(client), "").statusCode());
        var request = HttpRequest.newBuilder(uri("/internal/scheduler/tick"))
                .header("Authorization", "Bearer foundation-scheduler-test-secret-32-plus").GET().build();
        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void localTimerProcessesDurableProbeWithoutBrowserTraffic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO test_deadline_probe(id, deadline) VALUES (?, clock_timestamp() + interval '1 second')", id);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(1, jdbc.queryForObject("SELECT executions FROM test_deadline_probe WHERE id=?", Integer.class, id)));
    }

    @Test void repeatedMachineTriggersDoNotDuplicateProbeWork() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO test_deadline_probe(id, deadline) VALUES (?, clock_timestamp() - interval '1 second')", id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var call = (java.util.concurrent.Callable<Integer>) () -> client().send(
                    HttpRequest.newBuilder(uri("/internal/scheduler/tick"))
                            .header("Authorization", "Bearer foundation-scheduler-test-secret-32-plus")
                            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
            var first = executor.submit(call);
            var second = executor.submit(call);
            assertEquals(204, first.get());
            assertEquals(204, second.get());
        }
        assertEquals(1, jdbc.queryForObject("SELECT executions FROM test_deadline_probe WHERE id=?", Integer.class, id));
    }

    @Test void sessionsSurviveApplicationRestartAndSeedingDoesNotOverwriteAccounts() throws Exception {
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_account WHERE username='foundation-test'", String.class);
        var client = client();
        String[] args = {"--server.port=0", "--spring.profiles.active=local",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--app.demo.enabled=true", "--app.demo.username=foundation-test",
                "--app.demo.password=different-test-password"};
        int original = port;
        try {
            try (var first = new SpringApplicationBuilder(ClassroomGroupingApplication.class).run(args)) {
                port = ((WebServerApplicationContext) first).getWebServer().getPort();
                login(client);
            }
            try (var second = new SpringApplicationBuilder(ClassroomGroupingApplication.class).run(args)) {
                port = ((WebServerApplicationContext) second).getWebServer().getPort();
                assertEquals(200, get(client, "/api/auth/session").statusCode());
                assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM app_account WHERE username='foundation-test'", String.class));
                assertEquals(204, post(client, "/api/auth/logout", csrf(client), "").statusCode());
            }
        } finally { port = original; }
    }

    /** Test-only durable probe; never packaged into production or confused with allocation. */
    @TestConfiguration
    static class ProbeConfiguration {
        @Bean @Primary DueWork probeDueWork(JdbcTemplate jdbc) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS test_deadline_probe (id UUID PRIMARY KEY, deadline TIMESTAMPTZ NOT NULL, executions INT NOT NULL DEFAULT 0 CHECK (executions BETWEEN 0 AND 1))");
            return () -> jdbc.update("UPDATE test_deadline_probe SET executions=1 WHERE executions=0 AND deadline <= clock_timestamp()");
        }
    }
}
