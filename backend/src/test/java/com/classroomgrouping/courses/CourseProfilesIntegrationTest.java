package com.classroomgrouping.courses;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.CourseProfiles;
import com.classroomgrouping.courses.api.CourseWorkspace;
import com.classroomgrouping.courses.api.ProfileValidationException;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseProfilesIntegrationTest {
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
    @Autowired CourseProfiles profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private UUID setup(String prefix) {
        provisioning.provision(new Roster(List.of(
                new SeedAccount(prefix + "-teacher", "synthetic-password", "TEACHER", null, null),
                new SeedAccount(prefix + "-one", "synthetic-password", "STUDENT", "0001-" + prefix, "陳 Example"),
                new SeedAccount(prefix + "-two", "synthetic-password", "STUDENT", "0002-" + prefix, "Other Example"),
                new SeedAccount(prefix + "-outsider", "synthetic-password", "STUDENT", "0003-" + prefix, "Outside")),
                List.of(new SeedCourse(prefix + "-COURSE", "Synthetic Course", List.of(prefix + "-teacher"),
                        List.of(prefix + "-one", prefix + "-two")),
                        new SeedCourse(prefix + "-SECOND", "Second Course", List.of(prefix + "-teacher"), List.of(prefix + "-one")))));
        return workspace.list(() -> prefix + "-one").stream().filter(c -> c.code().endsWith("-COURSE")).findFirst().orElseThrow().id();
    }
    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private HttpResponse<String> request(HttpClient client, String path, String method, String body, String csrf, String contentType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (contentType != null) builder.header("Content-Type", contentType);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception { return request(client, path, "GET", "", null, null); }
    private String csrf(HttpClient client) throws Exception { return JsonPath.read(get(client, "/api/auth/csrf").body(), "$.token"); }
    private HttpClient login(String username) throws Exception {
        var client = client();
        assertEquals(204, request(client, "/api/auth/login", "POST", "username=" + username + "&password=synthetic-password",
                csrf(client), "application/x-www-form-urlencoded").statusCode());
        return client;
    }
    private HttpResponse<String> put(HttpClient client, UUID course, String body) throws Exception {
        return request(client, "/api/courses/" + course + "/about-me", "PUT", body, csrf(client), "application/json");
    }
    private String json(String text) { return mapper.writeValueAsString(Map.of("aboutMe", text)); }

    @Test void readsProjectExactIdentitiesWithoutCreatingIntroductionsOrExposingAssignments() throws Exception {
        var course = setup("reads");
        var client = login("reads-one");
        var before = jdbc.queryForList("SELECT * FROM course_introduction");
        var response = get(client, "/api/courses/" + course + "/classmates");
        assertEquals(200, response.statusCode());
        List<Map<String, Object>> classmates = JsonPath.read(response.body(), "$");
        assertEquals(2, classmates.size());
        assertEquals("0001-reads", classmates.getFirst().get("studentId"));
        assertEquals("陳 Example", classmates.getFirst().get("studentName"));
        for (var profile : classmates) {
            assertEquals(java.util.Set.of("accountId", "studentId", "studentName", "aboutMe"), profile.keySet());
            assertEquals("", profile.get("aboutMe"));
        }
        assertEquals("", JsonPath.read(get(client, "/api/courses/" + course + "/about-me").body(), "$.aboutMe"));
        assertEquals(before, jdbc.queryForList("SELECT * FROM course_introduction"));
    }
    @Test void savesOwnCourseTextExactlyAndOnlyOwnIntroduction() throws Exception {
        var course = setup("save");
        var client = login("save-one");
        String text = "  <script>alert('plain')</script>\r\n陳 😀 e\u0301  ";
        var response = put(client, course, json(text));
        assertEquals(200, response.statusCode());
        assertEquals(text, JsonPath.read(response.body(), "$.aboutMe"));
        assertEquals(text, profiles.classmates(() -> "save-two", course).getFirst().aboutMe());
        assertEquals("", profiles.own(() -> "save-two", course).aboutMe());
        var second = workspace.list(() -> "save-one").stream().filter(c -> !c.id().equals(course)).findFirst().orElseThrow().id();
        assertEquals("", profiles.own(() -> "save-one", second).aboutMe());
        assertEquals("0001-save", profiles.own(() -> "save-one", course).studentId());
        assertEquals(200, put(client, course, json("")).statusCode());
        assertEquals("", profiles.own(() -> "save-one", course).aboutMe());
    }
    @Test void countsUnicodeCodePointsAndFailedSavesPreservePriorValue() throws Exception {
        var course = setup("unicode");
        var client = login("unicode-one");
        String maximum = "😀".repeat(500);
        assertEquals(200, put(client, course, json(maximum)).statusCode());
        assertEquals(500, jdbc.queryForObject("SELECT char_length(about_me) FROM course_introduction WHERE course_id=?", Integer.class, course));
        for (String invalid : List.of("x".repeat(501), "😀".repeat(501), "e\u0301".repeat(251), "before\u0000after")) {
            var response = put(client, course, json(invalid));
            assertEquals(400, response.statusCode());
            assertEquals("INVALID_ABOUT_ME", JsonPath.read(response.body(), "$.code"));
            assertEquals(ProfileValidationException.MESSAGE, JsonPath.read(response.body(), "$.fieldErrors.aboutMe"));
            assertEquals(maximum, profiles.own(() -> "unicode-one", course).aboutMe());
        }
        for (String invalidJson : List.of("{}", "{\"aboutMe\":null}", "{\"aboutMe\":1}", "{\"aboutMe\":true}",
                "{\"aboutMe\":[]}", "{\"aboutMe\":{}}", "{\"aboutMe\":\"\\ud800\"}", "{\"aboutMe\":\"\\udc00\"}")) {
            assertEquals(400, put(client, course, invalidJson).statusCode());
            assertEquals(maximum, profiles.own(() -> "unicode-one", course).aboutMe());
        }
        for (String invalid : List.of("\ud800", "\udc00", "x\ud800x"))
            assertThrows(ProfileValidationException.class, () -> profiles.save(() -> "unicode-one", course, invalid));
        assertEquals(400, put(client, course, "{broken").statusCode());
    }
    @Test void realSessionsEnforceAuthenticationEnrollmentAndCsrfWithoutPeerWriteEndpoint() throws Exception {
        var course = setup("denial");
        var client = login("denial-one");
        profiles.save(() -> "denial-one", course, "unchanged");
        assertEquals(401, get(client(), "/api/courses/" + course + "/classmates").statusCode());
        assertEquals(401, get(client(), "/api/courses/" + course + "/about-me").statusCode());
        for (String endpoint : List.of("classmates", "about-me")) {
            assertEquals(403, get(login("denial-teacher"), "/api/courses/" + course + "/" + endpoint).statusCode());
            var outsider = login("denial-outsider");
            var inaccessible = get(outsider, "/api/courses/" + course + "/" + endpoint);
            var absent = get(outsider, "/api/courses/" + UUID.randomUUID() + "/" + endpoint);
            assertEquals(404, inaccessible.statusCode());
            assertEquals(inaccessible.body(), absent.body());
        }
        assertEquals(403, put(login("denial-teacher"), course, json("denied")).statusCode());
        assertEquals(404, put(login("denial-outsider"), course, json("denied")).statusCode());
        for (String token : List.of("", "invalid")) assertEquals(403, request(client, "/api/courses/" + course + "/about-me", "PUT",
                json("denied"), token.isEmpty() ? null : token, "application/json").statusCode());
        var otherAccount = profiles.own(() -> "denial-two", course).accountId();
        assertEquals(403, request(client, "/api/courses/" + course + "/classmates/" + otherAccount,
                "PUT", json("denied"), csrf(client), "application/json").statusCode());
        assertEquals(403, request(client, "/api/courses/" + course + "/about-me/" + otherAccount,
                "PUT", json("denied"), csrf(client), "application/json").statusCode());
        assertEquals("unchanged", profiles.own(() -> "denial-one", course).aboutMe());
        assertEquals("", profiles.own(() -> "denial-two", course).aboutMe());
        assertEquals(200, put(client, course, "{\"aboutMe\":\"self only\",\"accountId\":\"" + otherAccount + "\"}").statusCode());
        assertEquals("self only", profiles.own(() -> "denial-one", course).aboutMe());
        assertEquals("", profiles.own(() -> "denial-two", course).aboutMe());
    }
    @Test void serviceRechecksPersistedIdentityRoleAndEnrollment() {
        var course = setup("fresh");
        assertThrows(CourseNotAccessible.class, () -> profiles.save(() -> "fresh-teacher", course, "denied"));
        assertThrows(CourseNotAccessible.class, () -> profiles.classmates(() -> "fresh-outsider", course));
        var account = profiles.own(() -> "fresh-one", course).accountId();
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?", course, account);
        assertThrows(CourseNotAccessible.class, () -> profiles.own(() -> "fresh-one", course));
        assertThrows(CourseNotAccessible.class, () -> profiles.save(() -> "fresh-one", course, "denied"));
        var outsider = jdbc.queryForObject("SELECT id FROM app_account WHERE username='fresh-outsider'", UUID.class);
        jdbc.update("DELETE FROM student_identity WHERE account_id=?", outsider);
        jdbc.update("UPDATE app_account SET role='TEACHER' WHERE id=?", outsider);
        assertThrows(CourseNotAccessible.class, () -> profiles.save(() -> "fresh-outsider", course, "denied"));
    }
    @Test void databaseBackstopsRejectInvalidOrUnenrolledProfiles() {
        var course = setup("db");
        var student = profiles.own(() -> "db-one", course).accountId();
        var outsider = jdbc.queryForObject("SELECT id FROM app_account WHERE username='db-outsider'", UUID.class);
        for (String text : List.of("a".repeat(501), "😀".repeat(501))) assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO course_introduction VALUES (?,?,?)", course, student, text));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO course_introduction VALUES (?,?,NULL)", course, student));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO course_introduction VALUES (?,?,?)", course, outsider, "denied"));
        profiles.save(() -> "db-one", course, "valid");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO course_introduction VALUES (?,?,?)", course, student, "duplicate"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?", course, student));
    }
    @Test void concurrentFirstSavesUpsertExactlyOneWholeValue() throws Exception {
        var course = setup("race");
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { barrier.await(); return profiles.save(() -> "race-one", course, "first".repeat(80)); });
            var two = pool.submit(() -> { barrier.await(); return profiles.save(() -> "race-one", course, "second".repeat(80)); });
            assertEquals("first".repeat(80), one.get().aboutMe());
            assertEquals("second".repeat(80), two.get().aboutMe());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM course_introduction WHERE course_id=?", Integer.class, course));
        assertTrue(List.of("first".repeat(80), "second".repeat(80)).contains(profiles.own(() -> "race-one", course).aboutMe()));
    }
    @Test void v2UpgradePreservesExistingAccountsIdentitiesRelationshipsAndJdbcSessions() {
        String schema = "slice2_upgrade";
        jdbc.execute("CREATE SCHEMA " + schema);
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var flywayV2 = Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("2").load();
        flywayV2.migrate();
        var isolated = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID account = UUID.randomUUID(), course = UUID.randomUUID(), session = UUID.randomUUID();
        isolated.update("INSERT INTO app_account(id,username,password_hash,role) VALUES (?, 'upgrade-profile', 'preserved-hash', 'STUDENT')", account);
        isolated.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?, '00001', '陳 Upgrade')", account);
        isolated.update("INSERT INTO course VALUES (?, 'UPGRADE', 'Preserved Course')", course);
        isolated.update("INSERT INTO course_enrollment(course_id,account_id) VALUES (?,?)", course, account);
        isolated.update("INSERT INTO spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES (?,?,1,1,1800,1800001,'upgrade-profile')", session.toString(), UUID.randomUUID().toString());
        isolated.update("INSERT INTO spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) VALUES (?,'preserved',?)", session.toString(), new byte[]{1,2,3});
        var tables = List.of("app_account", "student_identity", "course", "course_enrollment", "spring_session", "spring_session_attributes");
        var before = new java.util.LinkedHashMap<String, List<Map<String,Object>>>();
        for (String table : tables) before.put(table, isolated.queryForList("SELECT * FROM " + table));
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for (String table : tables) {
            var after = isolated.queryForList("SELECT * FROM " + table);
            if (table.equals("spring_session_attributes")) assertArrayEquals(new byte[]{1,2,3}, (byte[]) after.getFirst().get("attribute_bytes"));
            else assertEquals(before.get(table), after);
        }
        assertEquals(0, isolated.queryForObject("SELECT count(*) FROM course_introduction", Integer.class));
        assertEquals(5, isolated.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
    }
}
