package com.classroomgrouping.grouping;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.grouping.api.ActivityConflict;
import com.classroomgrouping.grouping.api.GroupingActivities;
import com.classroomgrouping.grouping.api.GroupingActivities.CreateActivity;
import com.classroomgrouping.grouping.api.GroupingActivities.Workspace;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** Actual PostgreSQL lock chains, independent transactions, and database time establish every race. */
@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, properties = {
        "spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActivitySettingsConcurrencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired GroupingActivities activities;
    @Autowired RosterProvisioning provisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    private UUID setup(String p, int secondsUntilDeadline) {
        provisioning.provision(new Roster(List.of(
                new SeedAccount(p, "synthetic-password", "TEACHER", null, null),
                new SeedAccount(p + "1", "synthetic-password", "STUDENT", p + "001", "One"),
                new SeedAccount(p + "2", "synthetic-password", "STUDENT", p + "002", "Two")),
                List.of(new SeedCourse(p, "Settings " + p, List.of(p), List.of(p + "1", p + "2")))));
        UUID course = jdbc.queryForObject("SELECT id FROM course WHERE code=?", UUID.class, p);
        // Set the real deadline after password hashing/provisioning, so setup time cannot consume the race window.
        activities.create(() -> p, course, UUID.randomUUID(), new CreateActivity("Original", future(secondsUntilDeadline), List.of(2)));
        return course;
    }
    private Instant future(int seconds) {
        return jdbc.queryForObject("SELECT clock_timestamp() + make_interval(secs => ?)", OffsetDateTime.class, seconds).toInstant();
    }
    private Workspace read(String p, UUID course) { return activities.workspace(() -> p, course); }
    private Workspace text(String p, UUID course, UUID key, String value) {
        return activities.updateInstructions(() -> p, course, key, value);
    }
    private Workspace extend(String p, UUID course, UUID key, Instant value) {
        return activities.extendDeadline(() -> p, course, key, value);
    }
    private Connection lock(UUID course) throws Exception {
        var connection = dataSource.getConnection(); connection.setAutoCommit(false);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
        try (var statement = connection.prepareStatement("SELECT id FROM grouping_activity WHERE course_id=? FOR UPDATE")) {
            statement.setObject(1, course);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
        }
        return connection;
    }
    private int pid(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertTrue(rows.next()); return rows.getInt(1);
        }
    }
    private Workspace transaction(Supplier<Workspace> action) {
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> {
            assertEquals("read committed", jdbc.queryForObject("SHOW transaction_isolation", String.class));
            return action.get();
        });
    }
    private List<Integer> awaitWaiters(int blocker, int count) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            // Follow the entire chain: a later waiter may block behind an earlier queued command.
            var pids = jdbc.queryForList("""
                    WITH RECURSIVE waiting(pid) AS (
                      SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                      UNION
                      SELECT a.pid FROM pg_stat_activity a JOIN waiting w ON w.pid = ANY(pg_blocking_pids(a.pid))
                    ) SELECT pid FROM waiting
                    """, Integer.class, blocker);
            if (pids.size() >= count) {
                assertFalse(pids.contains(blocker));
                assertEquals(pids.size(), pids.stream().distinct().count());
                return pids;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < until);
        fail("Expected " + count + " distinct PostgreSQL lock waiters for PID " + blocker);
        return List.of();
    }
    private Object outcome(Future<Workspace> future) throws Exception {
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException failure) { return failure.getCause(); }
    }
    private void after(Instant deadline) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT clock_timestamp() < ?::timestamptz", Boolean.class, deadline.toString()))) {
            assertTrue(System.nanoTime() < until, "Database clock did not reach the real cutoff"); Thread.sleep(20);
        }
    }
    private List<?> snapshot(UUID course) {
        return List.of(jdbc.queryForList("SELECT * FROM grouping_activity WHERE course_id=?", course),
                jdbc.queryForList("SELECT p.* FROM activity_participant p JOIN grouping_activity a ON a.id=p.activity_id WHERE a.course_id=? ORDER BY p.account_id", course),
                jdbc.queryForList("SELECT g.* FROM activity_group g JOIN grouping_activity a ON a.id=g.activity_id WHERE a.course_id=? ORDER BY g.ordinal", course),
                jdbc.queryForList("SELECT r.* FROM grouping_command_receipt r JOIN grouping_activity a ON a.id=r.activity_id WHERE a.course_id=? ORDER BY r.command,r.command_key", course));
    }
    @Test void extensionWaitingAcrossCutoffUsesDatabaseClockAndRollsBackReceipt() throws Exception {
        String p = "settings-cutoff"; var course = setup(p, 3); var deadline = read(p, course).activity().deadline(); var before = snapshot(course);
        try (var pool = Executors.newSingleThreadExecutor(); var gate = lock(course)) {
            var result = pool.submit(() -> transaction(() -> extend(p, course, UUID.randomUUID(), deadline.plusSeconds(60))));
            awaitWaiters(pid(gate), 1); after(deadline); gate.commit();
            assertEquals("ACTIVITY_CLOSED", assertInstanceOf(ActivityConflict.class, outcome(result)).code());
        }
        assertEquals(before, snapshot(course));
    }
    @Test void competingExtensionsNeverMoveDeadlineBackward() throws Exception {
        String p = "settings-extend"; var course = setup(p, 3600); var deadline = read(p, course).activity().deadline();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var high = pool.submit(() -> transaction(() -> extend(p, course, UUID.randomUUID(), deadline.plusSeconds(120))));
            awaitWaiters(pid(gate), 1);
            var low = pool.submit(() -> transaction(() -> extend(p, course, UUID.randomUUID(), deadline.plusSeconds(60))));
            awaitWaiters(pid(gate), 2); gate.commit();
            assertInstanceOf(Workspace.class, outcome(high));
            var result = outcome(low);
            if (result instanceof ActivityConflict conflict) assertEquals("DEADLINE_NOT_LATER", conflict.code());
            else assertInstanceOf(Workspace.class, result);
        }
        assertEquals(deadline.plusSeconds(120), read(p, course).activity().deadline());
        assertEquals("Original", read(p, course).activity().instructions());
    }
    @Test void instructionAndExtensionRacePreservesBothNarrowChangesAndFixedStructure() throws Exception {
        String p = "settings-both"; var course = setup(p, 3600); var original = read(p, course).activity(); var deadline = original.deadline();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var instructions = pool.submit(() -> transaction(() -> text(p, course, UUID.randomUUID(), "Revised 👩🏽‍💻")));
            var extension = pool.submit(() -> transaction(() -> extend(p, course, UUID.randomUUID(), deadline.plusSeconds(60))));
            awaitWaiters(pid(gate), 2); gate.commit();
            assertInstanceOf(Workspace.class, outcome(instructions)); assertInstanceOf(Workspace.class, outcome(extension));
        }
        var saved = read(p, course).activity();
        assertEquals("Revised 👩🏽‍💻", saved.instructions()); assertEquals(deadline.plusSeconds(60), saved.deadline());
        assertEquals(original.groups(), saved.groups()); assertEquals(original.id(), saved.id());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM activity_participant WHERE activity_id=?", Integer.class, saved.id()));
    }
    @Test void simultaneousSameKeyEditsHaveOneReceiptAndReplayReturnsLatestTextAfterCutoff() throws Exception {
        String p = "settings-replay"; var course = setup(p, 3); var deadline = read(p, course).activity().deadline(); var key = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var first = pool.submit(() -> transaction(() -> text(p, course, key, "First exact text")));
            var second = pool.submit(() -> transaction(() -> text(p, course, key, "First exact text")));
            awaitWaiters(pid(gate), 2); gate.commit();
            assertEquals(assertInstanceOf(Workspace.class, outcome(first)), assertInstanceOf(Workspace.class, outcome(second)));
        }
        text(p, course, UUID.randomUUID(), "Newest text"); after(deadline);
        var before = snapshot(course);
        assertEquals("Newest text", text(p, course, key, "First exact text").activity().instructions());
        assertEquals("COMMAND_KEY_REUSED", assertThrows(ActivityConflict.class, () -> text(p, course, key, "First exact text ")).code());
        assertEquals(before, snapshot(course));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?", Integer.class, key));
    }
    @Test void committedExtensionReplayAfterCutoffReturnsCurrentProjectionWithoutReopening() throws Exception {
        String p = "settings-ext-replay"; var course = setup(p, 2); var deadline = read(p, course).activity().deadline(); var key = UUID.randomUUID();
        var extended = deadline.plusSeconds(2);
        extend(p, course, key, extended); text(p, course, UUID.randomUUID(), "Current after extension"); after(extended);
        var before = snapshot(course); var replay = extend(p, course, key, extended);
        assertEquals("FINALIZING", replay.activity().status()); assertEquals("Current after extension", replay.activity().instructions());
        assertEquals(extended, replay.activity().deadline()); assertEquals(before, snapshot(course));
        assertEquals("ACTIVITY_CLOSED", assertThrows(ActivityConflict.class, () -> extend(p, course, UUID.randomUUID(), extended.plusSeconds(60))).code());
    }
    @Test void teacherRevocationDuringActivityLockWaitRejectsBothCommandsIncludingReplay() throws Exception {
        String p = "settings-revoked"; var course = setup(p, 3600); var deadline = read(p, course).activity().deadline(); var key = UUID.randomUUID();
        text(p, course, key, "Receipt before revocation"); var before = snapshot(course);
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var edit = pool.submit(() -> transaction(() -> text(p, course, key, "Receipt before revocation")));
            var extension = pool.submit(() -> transaction(() -> extend(p, course, UUID.randomUUID(), deadline.plusSeconds(60))));
            awaitWaiters(pid(gate), 2);
            try (var remove = gate.prepareStatement("DELETE FROM course_teacher_assignment WHERE course_id=?")) {
                remove.setObject(1, course); assertEquals(1, remove.executeUpdate());
            }
            gate.commit();
            assertInstanceOf(CourseNotAccessible.class, outcome(edit)); assertInstanceOf(CourseNotAccessible.class, outcome(extension));
        }
        assertEquals(before, snapshot(course));
    }
    @Test void persistenceFailureRollsBackTextDeadlineAndReceiptAndOriginalKeysCanRetry() {
        String p = "settings-rollback"; var course = setup(p, 3600); var deadline = read(p, course).activity().deadline(); var before = snapshot(course);
        var editKey = UUID.randomUUID(); var extensionKey = UUID.randomUUID();
        String name = "reject_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.course_id='" + course
                + "'::uuid THEN RAISE EXCEPTION 'synthetic settings failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE UPDATE ON grouping_activity FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> text(p, course, editKey, "Retry text"));
            assertEquals(before, snapshot(course));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> extend(p, course, extensionKey, deadline.plusSeconds(60)));
            assertEquals(before, snapshot(course));
        } finally { jdbc.execute("DROP TRIGGER " + name + " ON grouping_activity"); jdbc.execute("DROP FUNCTION " + name + "()"); }
        text(p, course, editKey, "Retry text"); extend(p, course, extensionKey, deadline.plusSeconds(60));
        assertEquals("Retry text", read(p, course).activity().instructions());
        assertEquals(deadline.plusSeconds(60), read(p, course).activity().deadline());
    }
    @Test void statusReadsAcrossActualCutoffDoNotWriteAndPostCutoffTextRemainsEditable() throws Exception {
        String p = "settings-read"; var course = setup(p, 2); var deadline = read(p, course).activity().deadline(); var before = snapshot(course);
        assertEquals("OPEN", read(p, course).activity().status()); after(deadline);
        assertEquals("FINALIZING", read(p, course).activity().status());
        assertEquals("FINALIZING", activities.workspace(() -> p + "1", course).activity().status());
        assertEquals(before, snapshot(course));
        var saved = text(p, course, UUID.randomUUID(), "Instructions after real cutoff");
        assertEquals(deadline, saved.activity().deadline()); assertEquals("FINALIZING", saved.activity().status());
        assertEquals("Instructions after real cutoff", activities.workspace(() -> p + "1", course).activity().instructions());
    }
}
