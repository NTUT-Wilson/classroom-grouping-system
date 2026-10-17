package com.classroomgrouping.courses;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.CourseWorkspace;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import com.classroomgrouping.identity.api.ProvisioningConflict;
import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "app.demo.enabled=true", "app.demo.username=legacy-course-test",
                "app.demo.password=legacy-course-password", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseWorkspaceIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired RosterProvisioning provisioning;
    @Autowired CourseWorkspace workspace;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private Roster roster(String prefix) {
        return new Roster(List.of(new SeedAccount(prefix + "-teacher", "synthetic-teacher-password", "TEACHER", null, null),
                new SeedAccount(prefix + "-student", "synthetic-student-password", "STUDENT", "0000" + prefix, "陳 Example"),
                new SeedAccount(prefix + "-empty", "synthetic-student-password", "STUDENT", "0001" + prefix, "Empty Example")),
                List.of(new SeedCourse(prefix + "-COURSE", "Synthetic Course", List.of(prefix + "-teacher"), List.of(prefix + "-student"))));
    }
    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private HttpResponse<String> request(HttpClient client, int requestPort, String path, String method, String body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + requestPort + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (!method.equals("GET")) builder.header("Content-Type", "application/x-www-form-urlencoded");
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception { return request(client, port, path, "GET", "", null); }
    private String csrf(HttpClient client, int requestPort) throws Exception {
        return JsonPath.read(request(client, requestPort, "/api/auth/csrf", "GET", "", null).body(), "$.token");
    }
    private void login(HttpClient client, int requestPort, String username, String password) throws Exception {
        assertEquals(204, request(client, requestPort, "/api/auth/login", "POST",
                "username=" + username + "&password=" + password, csrf(client, requestPort)).statusCode());
    }
    @Test void rosterIsDisabledAndLegacyAccountHasNoFabricatedIdentityOrAccess() throws Exception {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_account WHERE username LIKE 'roster-%'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM student_identity i JOIN app_account a ON a.id=i.account_id WHERE a.username='legacy-course-test'", Integer.class));
        var client = client();
        login(client, port, "legacy-course-test", "legacy-course-password");
        var session = get(client, "/api/auth/session");
        assertEquals("STUDENT", JsonPath.read(session.body(), "$.role"));
        assertNotNull(JsonPath.read(session.body(), "$.accountId"));
        assertEquals("[]", get(client, "/api/courses").body());
    }
    @Test void repeatPreservesHashesIdsAndExactIdentity() {
        var roster = roster("repeat");
        provisioning.provision(roster);
        var before = jdbc.queryForList("SELECT id, password_hash FROM app_account WHERE username LIKE 'repeat-%' ORDER BY username");
        var course = workspace.list(() -> "repeat-teacher").getFirst();
        provisioning.provision(new Roster(roster.accounts().stream().map(a -> new SeedAccount(a.username(),
                "changed-synthetic-password", a.role(), a.studentId(), a.studentName())).toList(), roster.courses()));
        assertEquals(before, jdbc.queryForList("SELECT id, password_hash FROM app_account WHERE username LIKE 'repeat-%' ORDER BY username"));
        assertEquals(course, workspace.list(() -> "repeat-student").getFirst());
        assertEquals("0000repeat", jdbc.queryForObject("SELECT student_id FROM student_identity i JOIN app_account a ON a.id=i.account_id WHERE a.username='repeat-student'", String.class));
        assertEquals("陳 Example", jdbc.queryForObject("SELECT student_name FROM student_identity i JOIN app_account a ON a.id=i.account_id WHERE a.username='repeat-student'", String.class));
    }
    @Test void conflictingCourseOrIdentityOrRoleRollsBackWholeRoster() {
        provisioning.provision(roster("conflict"));
        var base = roster("conflict");
        var fresh = new SeedAccount("rollback-new", "synthetic-password", "TEACHER", null, null);
        assertThrows(ProvisioningConflict.class, () -> provisioning.provision(new Roster(List.of(fresh, base.accounts().getFirst()),
                List.of(new SeedCourse("conflict-COURSE", "Changed name", List.of("conflict-teacher"), List.of())))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_account WHERE username='rollback-new'", Integer.class));
        var student = base.accounts().get(1);
        assertThrows(ProvisioningConflict.class, () -> provisioning.provision(new Roster(List.of(new SeedAccount(student.username(),
                student.password(), "STUDENT", "changed-identity", student.studentName())), List.of())));
        assertThrows(ProvisioningConflict.class, () -> provisioning.provision(new Roster(List.of(new SeedAccount("conflict-teacher",
                "synthetic-password", "STUDENT", "changed-role", "Changed")), List.of())));
        var invalid = new SeedAccount("invalid-new", "synthetic-password", "STUDENT", "＝123", "Unsafe");
        assertThrows(IllegalArgumentException.class, () -> provisioning.provision(new Roster(List.of(fresh, invalid), List.of())));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_account WHERE username IN ('invalid-new','rollback-new')", Integer.class));
    }
    @Test void concurrentProvisioningCreatesExactlyOneStableRoster() throws Exception {
        var roster = roster("concurrent");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.Callable<Void> call = () -> { start.await(); provisioning.provision(roster); return null; };
            var one = pool.submit(call);
            var two = pool.submit(call);
            one.get(); two.get();
        }
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM app_account WHERE username LIKE 'concurrent-%'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM course WHERE code='concurrent-COURSE'", Integer.class));
        assertEquals(1, workspace.list(() -> "concurrent-student").size());
    }
    @Test void databaseRejectsWrongRolesAndDuplicateRelationships() {
        provisioning.provision(roster("integrity"));
        UUID course = workspace.list(() -> "integrity-teacher").getFirst().id();
        UUID teacher = jdbc.queryForObject("SELECT id FROM app_account WHERE username='integrity-teacher'", UUID.class);
        UUID student = jdbc.queryForObject("SELECT id FROM app_account WHERE username='integrity-student'", UUID.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO course_teacher_assignment(course_id, account_id) VALUES (?, ?)", course, student));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO course_enrollment(course_id, account_id) VALUES (?, ?)", course, teacher));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO course_enrollment(course_id, account_id) VALUES (?, ?)", course, student));
    }
    @Test void realSessionListsAndDirectAccessEnforceRelationshipsAndDenyEveryMutation() throws Exception {
        provisioning.provision(roster("access")); provisioning.provision(roster("other"));
        UUID own = workspace.list(() -> "access-teacher").getFirst().id();
        UUID other = workspace.list(() -> "other-teacher").getFirst().id();
        assertEquals(401, get(client(), "/api/courses").statusCode());
        for (String role : List.of("teacher", "student")) {
            var client = client(); login(client, port, "access-" + role, "synthetic-" + role + "-password");
            assertEquals(1, ((List<?>) JsonPath.read(get(client, "/api/courses").body(), "$" )).size());
            assertEquals(200, get(client, "/api/courses/" + own).statusCode());
            var denied = get(client, "/api/courses/" + other);
            var absent = get(client, "/api/courses/" + UUID.randomUUID());
            assertEquals(404, denied.statusCode()); assertEquals(denied.body(), absent.body());
            assertEquals("COURSE_NOT_ACCESSIBLE", JsonPath.read(denied.body(), "$.code"));
            assertFalse(denied.body().contains("other"));
            for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
                assertEquals(403, request(client, port, "/api/courses/" + own, method, "", csrf(client, port)).statusCode());
            }
        }
        assertThrows(CourseNotAccessible.class, () -> workspace.detail(() -> "access-empty", own));
        assertThrows(CourseNotAccessible.class, () -> workspace.detail(() -> "other-student", own));
    }
    @Test void serviceRechecksCurrentPersistedRoleInsteadOfSessionAuthorities() throws Exception {
        provisioning.provision(roster("rolechange"));
        var client = client(); login(client, port, "rolechange-empty", "synthetic-student-password");
        jdbc.update("DELETE FROM student_identity WHERE account_id=(SELECT id FROM app_account WHERE username='rolechange-empty')");
        jdbc.update("UPDATE app_account SET role='TEACHER' WHERE username='rolechange-empty'");
        assertEquals("TEACHER", JsonPath.read(get(client, "/api/auth/session").body(), "$.role"));
        assertEquals("[]", get(client, "/api/courses").body());
    }
    @Test void upgradeFromV1PreservesPasswordAccountAndAuthenticatedJdbcSession() throws Exception {
        jdbc.execute("CREATE SCHEMA slice1_upgrade");
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=slice1_upgrade";
        String[] common = {"--server.port=0", "--spring.profiles.active=local", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + POSTGRES.getUsername(), "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--app.demo.enabled=true", "--app.demo.username=upgrade-legacy", "--app.demo.password=upgrade-original-password"};
        var client = client();
        UUID oldId; String oldHash;
        var v1 = java.util.Arrays.copyOf(common, common.length + 1); v1[common.length] = "--spring.flyway.target=1";
        try (var first = new SpringApplicationBuilder(ClassroomGroupingApplication.class).run(v1)) {
            int firstPort = ((WebServerApplicationContext) first).getWebServer().getPort();
            login(client, firstPort, "upgrade-legacy", "upgrade-original-password");
            var firstJdbc = first.getBean(JdbcTemplate.class);
            assertEquals("slice1_upgrade", firstJdbc.queryForObject("SELECT current_schema()", String.class));
            assertEquals(1, firstJdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
            oldId = firstJdbc.queryForObject("SELECT id FROM app_account WHERE username='upgrade-legacy'", UUID.class);
            oldHash = firstJdbc.queryForObject("SELECT password_hash FROM app_account WHERE username='upgrade-legacy'", String.class);
        }
        try (var upgraded = new SpringApplicationBuilder(ClassroomGroupingApplication.class).run(common)) {
            int upgradedPort = ((WebServerApplicationContext) upgraded).getWebServer().getPort();
            var upgradedJdbc = upgraded.getBean(JdbcTemplate.class);
            var session = request(client, upgradedPort, "/api/auth/session", "GET", "", null);
            assertEquals(200, session.statusCode());
            assertEquals(oldId.toString(), JsonPath.read(session.body(), "$.accountId"));
            assertEquals(oldHash, upgradedJdbc.queryForObject("SELECT password_hash FROM app_account WHERE username='upgrade-legacy'", String.class));
            assertTrue(encoder.matches("upgrade-original-password", oldHash));
            assertEquals("[]", request(client, upgradedPort, "/api/courses", "GET", "", null).body());
            assertEquals(9, upgradedJdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        }
    }
}
