package com.classroomgrouping.courses.internal;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.grouping.api.GroupingActivities;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, properties = {
        "spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BatchFixtureSetupIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RosterProvisioning provisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupingActivities activities;

    @Test void optInRequiresValidPasswordAndRepeatPreservesIdentityCredentialsAndProfileHistory() throws Exception {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_account", Integer.class));
        var setup = new BatchFixtureSetup();
        var args = new DefaultApplicationArguments();
        setup.seedBatchFixture(provisioning, false, "").run(args);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_account", Integer.class));
        for (var invalid : new String[]{"", "too-short", "界".repeat(25)}) {
            assertThrows(IllegalArgumentException.class, () -> setup.seedBatchFixture(provisioning, true, invalid).run(args));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_account", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM course", Integer.class));
        }
        setup.seedBatchFixture(provisioning, true, "synthetic-fixture-password").run(args);
        var accounts = jdbc.queryForList("SELECT id, username, password_hash, role FROM app_account WHERE username LIKE 'batch-%' ORDER BY username");
        var identities = jdbc.queryForList("SELECT account_id, student_id, student_name FROM student_identity WHERE student_id LIKE '0000700%' ORDER BY student_id");
        assertEquals(9, accounts.size()); assertEquals(8, identities.size());
        assertEquals("00007001", identities.getFirst().get("student_id"));
        assertEquals("陳 Batch 一", identities.getFirst().get("student_name"));
        assertEquals(36, jdbc.queryForObject("SELECT count(*) FROM course_enrollment", Integer.class));
        assertEquals(9, jdbc.queryForObject("SELECT count(*) FROM course_teacher_assignment", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM grouping_activity", Integer.class));
        var batchCourses = jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'BATCH-%' ORDER BY code");
        var batchEnrollments = jdbc.queryForList("SELECT course_id, account_id FROM course_enrollment ORDER BY course_id, account_id");
        var batchTeachers = jdbc.queryForList("SELECT course_id, account_id FROM course_teacher_assignment ORDER BY course_id, account_id");
        var courseId = jdbc.queryForObject("SELECT id FROM course WHERE code='BATCH-101'", UUID.class);
        var accountId = jdbc.queryForObject("SELECT id FROM app_account WHERE username='batch-student-1'", UUID.class);
        jdbc.update("INSERT INTO course_introduction(course_id, account_id, about_me) VALUES (?, ?, ?)",
                courseId, accountId, "Existing synthetic introduction 👩🏽‍💻");
        new DemoRosterSetup().seedDemoRoster(provisioning, "synthetic-teacher-password", "synthetic-student-password").run(args);
        var demoCourses = jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'DEMO-%' ORDER BY code");
        var demoEnrollments = jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'DEMO-%' ORDER BY c.code");
        activities.create(() -> "batch-teacher", courseId, UUID.randomUUID(),
                new GroupingActivities.CreateActivity("Existing activity history", Instant.parse("2099-03-15T06:30:00Z"), List.of(2,2,2,2)));
        var activityHistory = List.of(jdbc.queryForList("SELECT * FROM grouping_activity ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_group ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_participant ORDER BY activity_id,account_id"));
        setup.seedBatchFixture(provisioning, true, "changed-fixture-password").run(args);
        assertEquals(activityHistory, List.of(jdbc.queryForList("SELECT * FROM grouping_activity ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_group ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_participant ORDER BY activity_id,account_id")));
        assertEquals(accounts, jdbc.queryForList("SELECT id, username, password_hash, role FROM app_account WHERE username LIKE 'batch-%' ORDER BY username"));
        assertEquals(identities, jdbc.queryForList("SELECT account_id, student_id, student_name FROM student_identity WHERE student_id LIKE '0000700%' ORDER BY student_id"));
        assertEquals("Existing synthetic introduction 👩🏽‍💻", jdbc.queryForObject(
                "SELECT about_me FROM course_introduction WHERE course_id=? AND account_id=?", String.class, courseId, accountId));
        assertEquals(38, jdbc.queryForObject("SELECT count(*) FROM course_enrollment", Integer.class));
        assertEquals(batchCourses, jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'BATCH-%' ORDER BY code"));
        assertEquals(batchEnrollments, jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'BATCH-%' ORDER BY e.course_id, e.account_id"));
        assertEquals(batchTeachers, jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_teacher_assignment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'BATCH-%' ORDER BY e.course_id, e.account_id"));
        assertEquals(demoCourses, jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'DEMO-%' ORDER BY code"));
        assertEquals(demoEnrollments, jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'DEMO-%' ORDER BY c.code"));
    }
}
