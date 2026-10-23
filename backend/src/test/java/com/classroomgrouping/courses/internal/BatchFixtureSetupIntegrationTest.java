package com.classroomgrouping.courses.internal;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.fixtures.V2GroupingFixture;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import com.classroomgrouping.grouping.api.GroupingActivities;
import com.classroomgrouping.grouping.api.GroupingMemberships;
import com.classroomgrouping.grouping.api.GroupingInvitations;
import com.classroomgrouping.grouping.api.GroupingApplications;
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
    @Autowired GroupingMemberships memberships;
    @Autowired GroupingInvitations invitations;
    @Autowired GroupingApplications applications;

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
        // Simulate a populated legacy install before the additive V2 fixture upgrade.
        provisioning.provision(new RosterProvisioning.Roster(List.of(
                new SeedAccount("batch-teacher", "original-teacher-password", "TEACHER", null, null),
                new SeedAccount("batch-student-1", "original-student-password", "STUDENT", "00007001", "陳 Batch 一")),
                List.of(new RosterProvisioning.SeedCourse("BATCH-101", "Batch Profile Classroom",
                        List.of("batch-teacher"), List.of("batch-student-1")))));
        var legacyAccounts = jdbc.queryForList("SELECT * FROM app_account ORDER BY username");
        var legacyCourse = jdbc.queryForList("SELECT * FROM course WHERE code='BATCH-101'");
        setup.seedBatchFixture(provisioning, true, "synthetic-fixture-password").run(args);
        assertEquals(legacyAccounts, jdbc.queryForList("SELECT * FROM app_account WHERE username IN ('batch-teacher','batch-student-1') ORDER BY username"));
        assertEquals(legacyCourse, jdbc.queryForList("SELECT * FROM course WHERE code='BATCH-101'"));
        var accounts = jdbc.queryForList("SELECT id, username, password_hash, role FROM app_account WHERE username LIKE 'batch-%' ORDER BY username");
        var identities = jdbc.queryForList("SELECT account_id, student_id, student_name FROM student_identity WHERE student_id LIKE '0000700%' ORDER BY student_id");
        assertEquals(9, accounts.size()); assertEquals(8, identities.size());
        assertEquals("00007001", identities.getFirst().get("student_id"));
        assertEquals("陳 Batch 一", identities.getFirst().get("student_name"));
        assertEquals(85, jdbc.queryForObject("SELECT count(*) FROM course_enrollment", Integer.class));
        assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM course_teacher_assignment", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code='BATCH-707'", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code='BATCH-708'", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code='BATCH-1001'", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code='BATCH-1002'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM grouping_activity", Integer.class));
        var batchCourses = jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'BATCH-%' ORDER BY code");
        var batchEnrollments = jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'BATCH-%' ORDER BY e.course_id, e.account_id");
        var batchTeachers = jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_teacher_assignment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'BATCH-%' ORDER BY e.course_id, e.account_id");
        var v2Accounts = jdbc.queryForList("SELECT * FROM app_account WHERE username LIKE 'v2-%' ORDER BY username");
        var v2Identities = jdbc.queryForList("SELECT * FROM student_identity WHERE student_id LIKE '000080%' ORDER BY student_id");
        var v2Courses = jdbc.queryForList("SELECT * FROM course WHERE code LIKE 'V2-%' ORDER BY code");
        assertEquals(13, v2Accounts.size());
        assertEquals(10, v2Identities.size());
        assertEquals(4, v2Courses.size());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM course_enrollment e JOIN app_account a ON a.id=e.account_id WHERE a.username='v2-late-student'", Integer.class));
        var courseId = jdbc.queryForObject("SELECT id FROM course WHERE code='BATCH-101'", UUID.class);
        var accountId = jdbc.queryForObject("SELECT id FROM app_account WHERE username='batch-student-1'", UUID.class);
        jdbc.update("INSERT INTO course_introduction(course_id, account_id, about_me) VALUES (?, ?, ?)",
                courseId, accountId, "Existing synthetic introduction 👩🏽‍💻");
        new DemoRosterSetup().seedDemoRoster(provisioning, "synthetic-teacher-password", "synthetic-student-password").run(args);
        var demoCourses = jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'DEMO-%' ORDER BY code");
        var demoEnrollments = jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'DEMO-%' ORDER BY c.code");
        activities.create(() -> "batch-teacher", courseId, UUID.randomUUID(),
                new GroupingActivities.CreateActivity("Existing activity history", Instant.parse("2099-03-15T06:30:00Z"), List.of(2,2,2,2)));
        var group=memberships.discover(() -> "batch-student-1",courseId).groups().getFirst().id();
        memberships.joinEmpty(() -> "batch-student-1",courseId,group,UUID.randomUUID());
        var recipient=jdbc.queryForObject("SELECT id FROM app_account WHERE username='batch-student-2'",UUID.class);
        var rejected=invitations.invite(() -> "batch-student-1",courseId,group,recipient,UUID.randomUUID()).items().getFirst().id();
        invitations.decide(() -> "batch-student-2",courseId,rejected,UUID.randomUUID(),"REJECT");
        invitations.invite(() -> "batch-student-1",courseId,group,recipient,UUID.randomUUID());
        applications.apply(() -> "batch-student-3",courseId,group,UUID.randomUUID());
        var v2Course = jdbc.queryForObject("SELECT id FROM course WHERE code='V2-101'", UUID.class);
        var v2 = V2GroupingFixture.create(activities, memberships, applications, invitations, v2Course,
                account("v2-student-6"), account("v2-student-7"), Instant.parse("2099-03-15T06:30:00Z"));
        var groups = memberships.discover(() -> "v2-student-3", v2Course).groups();
        assertEquals(1, groups.get(0).members().size());
        assertEquals(account("v2-student-1"), groups.get(0).manager().accountId());
        assertEquals(2, groups.get(1).members().size());
        assertEquals(account("v2-student-2"), groups.get(1).manager().accountId());
        assertEquals(activities.workspace(() -> "v2-teacher", v2Course), activities.workspace(() -> "v2-co-teacher", v2Course));
        assertNull(activities.workspace(() -> "v2-teacher",
                jdbc.queryForObject("SELECT id FROM course WHERE code='V2-202'", UUID.class)).activity());
        assertEquals(List.of("ACCEPTED", "PENDING", "REJECTED"), jdbc.queryForList(
                "SELECT status FROM application_attempt WHERE activity_id=(SELECT id FROM grouping_activity WHERE course_id=?) ORDER BY status", String.class, v2Course));
        assertEquals(List.of("PENDING", "REJECTED"), jdbc.queryForList(
                "SELECT status FROM invitation_attempt WHERE activity_id=(SELECT id FROM grouping_activity WHERE course_id=?) ORDER BY status", String.class, v2Course));
        var receiptCount = jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt", Integer.class);
        for (var outsider : List.of("v2-outsider-teacher", "v2-outsider-student", "v2-late-student")) {
            assertThrows(CourseNotAccessible.class, () -> activities.workspace(() -> outsider, v2Course));
            assertThrows(CourseNotAccessible.class, () -> memberships.joinEmpty(() -> outsider, v2Course, v2.soleManagerGroupId(), UUID.randomUUID()));
        }
        for (var actor : List.of("v2-student-3", "v2-teacher", "v2-co-teacher")) {
            assertThrows(CourseNotAccessible.class, () -> applications.decide(() -> actor, v2Course, v2.pendingApplicationId(), UUID.randomUUID(), "APPROVE"));
            assertThrows(CourseNotAccessible.class, () -> invitations.invite(() -> actor, v2Course, v2.multiMemberGroupId(), account("v2-student-8"), UUID.randomUUID()));
        }
        // Enrollment after creation grants course access, never fixed activity participation.
        provisioning.provision(new RosterProvisioning.Roster(List.of(new SeedAccount("v2-late-student",
                "unused-new-fixture-password", "STUDENT", "00008010", "Jamie Late")),
                List.of(new RosterProvisioning.SeedCourse("V2-101", "V2 Lifecycle Classroom", List.of(), List.of("v2-late-student")))));
        assertThrows(CourseNotAccessible.class, () -> activities.workspace(() -> "v2-late-student", v2Course));
        assertThrows(CourseNotAccessible.class, () -> memberships.joinEmpty(() -> "v2-late-student", v2Course, v2.soleManagerGroupId(), UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> applications.apply(() -> "v2-late-student", v2Course, v2.soleManagerGroupId(), UUID.randomUUID()));
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM activity_participant WHERE activity_id=(SELECT id FROM grouping_activity WHERE course_id=?)", Integer.class, v2Course));
        assertEquals(receiptCount, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt", Integer.class));
        var requestHistory=List.of(jdbc.queryForList("SELECT * FROM invitation_attempt ORDER BY id"),
                jdbc.queryForList("SELECT * FROM application_attempt ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_membership ORDER BY account_id"),
                jdbc.queryForList("SELECT * FROM grouping_command_receipt ORDER BY command, command_key"));
        var activityHistory = List.of(jdbc.queryForList("SELECT * FROM grouping_activity ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_group ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_participant ORDER BY activity_id,account_id"));
        var v2Teachers = jdbc.queryForList("SELECT e.* FROM course_teacher_assignment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'V2-%' ORDER BY e.course_id,e.account_id");
        var v2Enrollments = jdbc.queryForList("SELECT e.* FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'V2-%' ORDER BY e.course_id,e.account_id");
        var noActivityCourse = jdbc.queryForObject("SELECT id FROM course WHERE code='V2-202'", UUID.class);
        var cutoffCourse = jdbc.queryForObject("SELECT id FROM course WHERE code='V2-404'", UUID.class);
        assertNull(activities.workspace(() -> "v2-co-teacher", noActivityCourse).activity());
        assertNull(activities.workspace(() -> "v2-student-8", cutoffCourse).activity());
        assertEquals("ABSENT", memberships.discover(() -> "v2-student-8", cutoffCourse).status());
        jdbc.update("DELETE FROM course_teacher_assignment WHERE course_id=(SELECT id FROM course WHERE code='V2-202') AND account_id=?", account("v2-co-teacher"));
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=(SELECT id FROM course WHERE code='V2-404') AND account_id=?", account("v2-student-8"));
        assertThrows(CourseNotAccessible.class, () -> activities.workspace(() -> "v2-co-teacher", noActivityCourse));
        assertThrows(CourseNotAccessible.class, () -> activities.workspace(() -> "v2-student-8", cutoffCourse));
        assertThrows(CourseNotAccessible.class, () -> memberships.discover(() -> "v2-student-8", cutoffCourse));
        setup.seedBatchFixture(provisioning, true, "changed-fixture-password").run(args);
        assertNull(activities.workspace(() -> "v2-co-teacher", noActivityCourse).activity());
        assertNull(activities.workspace(() -> "v2-student-8", cutoffCourse).activity());
        assertEquals("ABSENT", memberships.discover(() -> "v2-student-8", cutoffCourse).status());
        assertEquals(v2Teachers, jdbc.queryForList("SELECT e.* FROM course_teacher_assignment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'V2-%' ORDER BY e.course_id,e.account_id"));
        assertEquals(v2Enrollments, jdbc.queryForList("SELECT e.* FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'V2-%' ORDER BY e.course_id,e.account_id"));
        assertEquals(requestHistory,List.of(jdbc.queryForList("SELECT * FROM invitation_attempt ORDER BY id"),
                jdbc.queryForList("SELECT * FROM application_attempt ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_membership ORDER BY account_id"),
                jdbc.queryForList("SELECT * FROM grouping_command_receipt ORDER BY command, command_key")));
        assertEquals(activityHistory, List.of(jdbc.queryForList("SELECT * FROM grouping_activity ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_group ORDER BY id"),
                jdbc.queryForList("SELECT * FROM activity_participant ORDER BY activity_id,account_id")));
        assertEquals(accounts, jdbc.queryForList("SELECT id, username, password_hash, role FROM app_account WHERE username LIKE 'batch-%' ORDER BY username"));
        assertEquals(v2Accounts, jdbc.queryForList("SELECT * FROM app_account WHERE username LIKE 'v2-%' ORDER BY username"));
        assertEquals(v2Identities, jdbc.queryForList("SELECT * FROM student_identity WHERE student_id LIKE '000080%' ORDER BY student_id"));
        assertEquals(v2Courses, jdbc.queryForList("SELECT * FROM course WHERE code LIKE 'V2-%' ORDER BY code"));
        assertEquals(identities, jdbc.queryForList("SELECT account_id, student_id, student_name FROM student_identity WHERE student_id LIKE '0000700%' ORDER BY student_id"));
        assertEquals("Existing synthetic introduction 👩🏽‍💻", jdbc.queryForObject(
                "SELECT about_me FROM course_introduction WHERE course_id=? AND account_id=?", String.class, courseId, accountId));
        assertEquals(88, jdbc.queryForObject("SELECT count(*) FROM course_enrollment", Integer.class));
        assertEquals(batchCourses, jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'BATCH-%' ORDER BY code"));
        assertEquals(batchEnrollments, jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'BATCH-%' ORDER BY e.course_id, e.account_id"));
        assertEquals(batchTeachers, jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_teacher_assignment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'BATCH-%' ORDER BY e.course_id, e.account_id"));
        assertEquals(demoCourses, jdbc.queryForList("SELECT id, code, name FROM course WHERE code LIKE 'DEMO-%' ORDER BY code"));
        assertEquals(demoEnrollments, jdbc.queryForList("SELECT e.course_id, e.account_id FROM course_enrollment e JOIN course c ON c.id=e.course_id WHERE c.code LIKE 'DEMO-%' ORDER BY c.code"));
    }

    private UUID account(String username) {
        return jdbc.queryForObject("SELECT id FROM app_account WHERE username=?", UUID.class, username);
    }
}
