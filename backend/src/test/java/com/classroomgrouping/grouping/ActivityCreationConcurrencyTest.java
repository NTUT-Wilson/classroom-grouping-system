package com.classroomgrouping.grouping;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.grouping.api.ActivityConflict;
import com.classroomgrouping.grouping.api.ActivityValidationException;
import com.classroomgrouping.grouping.api.GroupingActivities;
import com.classroomgrouping.grouping.api.GroupingActivities.CreateActivity;
import com.classroomgrouping.grouping.api.GroupingActivities.Workspace;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Real PostgreSQL waits are observed in pg_stat_activity, rather than inferred from sleeps. */
@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class,
        properties = {"spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActivityCreationConcurrencyTest {
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

    private List<SeedAccount> accounts(String p) {
        return List.of(new SeedAccount(p + "-teacher", "synthetic-password", "TEACHER", null, null),
                new SeedAccount(p + "-one", "synthetic-password", "STUDENT", p + "-001", "One"),
                new SeedAccount(p + "-two", "synthetic-password", "STUDENT", p + "-002", "Two"),
                new SeedAccount(p + "-three", "synthetic-password", "STUDENT", p + "-003", "Three"));
    }
    private SeedCourse seed(String p, String suffix, List<String> students) {
        return new SeedCourse(p + suffix, "Concurrency " + p + suffix, List.of(p + "-teacher"), students);
    }
    private UUID setup(String p) {
        provisioning.provision(new Roster(accounts(p), List.of(seed(p, "-A", List.of(p + "-one", p + "-two")),
                seed(p, "-B", List.of(p + "-one", p + "-two")))));
        return course(p, "-A");
    }
    private UUID course(String p, String suffix) {
        return jdbc.queryForObject("SELECT id FROM course WHERE code=?", UUID.class, p + suffix);
    }
    private CreateActivity input() { return new CreateActivity("Exact instructions", Instant.now().plusSeconds(3600), List.of(2)); }
    private Workspace create(String p, UUID course, UUID key, CreateActivity input) {
        return activities.create(() -> p + "-teacher", course, key, input);
    }
    private Connection lockCourse(UUID course) throws Exception {
        var connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
        try (var statement = connection.prepareStatement("SELECT id FROM course WHERE id=? FOR UPDATE")) {
            statement.setObject(1, course);
            try (var result = statement.executeQuery()) { assertTrue(result.next()); }
        }
        return connection;
    }
    private int pid(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertTrue(result.next()); return result.getInt(1);
        }
    }
    private List<Integer> waiters(int blocker, int minimum) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            var pids = jdbc.queryForList("SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))", Integer.class, blocker);
            if (pids.size() >= minimum) {
                assertFalse(pids.contains(blocker));
                assertEquals(pids.size(), pids.stream().distinct().count());
                return pids;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < until);
        fail("Expected distinct PostgreSQL transactions blocked on PID " + blocker);
        return List.of();
    }
    private Object outcome(Future<Workspace> future) throws Exception {
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException failure) { return failure.getCause(); }
    }
    private void assertCounts(UUID course, int activity, int participants, int groups, int receipts) {
        assertEquals(activity, jdbc.queryForObject("SELECT count(*) FROM grouping_activity WHERE course_id=?", Integer.class, course));
        assertEquals(participants, jdbc.queryForObject("SELECT count(*) FROM activity_participant p JOIN grouping_activity a ON a.id=p.activity_id WHERE a.course_id=?", Integer.class, course));
        assertEquals(groups, jdbc.queryForObject("SELECT count(*) FROM activity_group g JOIN grouping_activity a ON a.id=g.activity_id WHERE a.course_id=?", Integer.class, course));
        assertEquals(receipts, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt r JOIN grouping_activity a ON a.id=r.activity_id WHERE a.course_id=?", Integer.class, course));
    }

    @Test void distinctConcurrentCommandsCreateExactlyOneWholeActivity() throws Exception {
        String p = "distinct"; var course = setup(p); var input = input();
        try (var pool = Executors.newFixedThreadPool(2); var blocker = lockCourse(course)) {
            var first = pool.submit(() -> create(p, course, UUID.randomUUID(), input));
            var second = pool.submit(() -> create(p, course, UUID.randomUUID(), input));
            // PostgreSQL can queue the second waiter behind the first: count the whole lock chain.
            waiters(pid(blocker), 1);
            awaitBlockedTransactions(2);
            blocker.commit();
            var results = List.of(outcome(first), outcome(second));
            assertEquals(1, results.stream().filter(Workspace.class::isInstance).count());
            assertEquals(1, results.stream().filter(ActivityConflict.class::isInstance).count());
            assertEquals("ACTIVITY_EXISTS", ((ActivityConflict) results.stream().filter(ActivityConflict.class::isInstance).findFirst().orElseThrow()).code());
        }
        assertCounts(course, 1, 2, 1, 1);
    }
    private void awaitBlockedTransactions(int count) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Integer.class) >= count) return;
            Thread.sleep(20);
        } while (System.nanoTime() < until);
        fail("Concurrent commands did not reach distinct database lock waits");
    }
    @Test void identicalConcurrentKeysReturnSameCommittedActivity() throws Exception {
        String p = "samekey"; var course = setup(p); var key = UUID.randomUUID(); var input = input();
        try (var pool = Executors.newFixedThreadPool(2); var blocker = lockCourse(course)) {
            var first = pool.submit(() -> create(p, course, key, input));
            var second = pool.submit(() -> create(p, course, key, input));
            waiters(pid(blocker), 1); awaitBlockedTransactions(2); blocker.commit();
            var one = assertInstanceOf(Workspace.class, outcome(first));
            assertEquals(one, assertInstanceOf(Workspace.class, outcome(second)));
        }
        assertCounts(course, 1, 2, 1, 1);
    }
    @Test void sameActorKeyCannotCreateInTwoCoursesInParallelOrChangeExactInput() throws Exception {
        String p = "crosskey"; var a = setup(p); var b = course(p, "-B"); var key = UUID.randomUUID(); var input = input();
        try (var pool = Executors.newFixedThreadPool(2); var blocker = lockCourse(a); var secondBlocker = lockCourse(b)) {
            var first = pool.submit(() -> create(p, a, key, input));
            waiters(pid(blocker), 1);
            var second = pool.submit(() -> create(p, b, key, input));
            waiters(pid(secondBlocker), 1);
            assertNotEquals(pid(blocker), pid(secondBlocker));
            blocker.commit();
            secondBlocker.commit();
            var results = List.of(outcome(first), outcome(second));
            assertEquals(1, results.stream().filter(Workspace.class::isInstance).count());
            assertEquals("COMMAND_KEY_REUSED", assertInstanceOf(ActivityConflict.class,
                    results.stream().filter(ActivityConflict.class::isInstance).findFirst().orElseThrow()).code());
            var winner = assertInstanceOf(Workspace.class, results.stream().filter(Workspace.class::isInstance).findFirst().orElseThrow());
            assertEquals("COMMAND_KEY_REUSED", assertThrows(ActivityConflict.class, () -> create(p, winner.activity().courseId(), key,
                    new CreateActivity(input.instructions() + " ", input.deadline(), input.capacities()))).code());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM grouping_activity WHERE course_id IN (?,?)", Integer.class, a, b));
    }
    @Test void lockWaitCrossingFutureDeadlineRejectsWithoutPartialState() throws Exception {
        String p = "cutoff"; var course = setup(p);
        Instant deadline = jdbc.queryForObject("SELECT clock_timestamp() + interval '2 seconds'", java.time.OffsetDateTime.class).toInstant();
        try (var pool = Executors.newSingleThreadExecutor(); var blocker = lockCourse(course)) {
            var command = pool.submit(() -> create(p, course, UUID.randomUUID(), new CreateActivity("Deadline wait", deadline, List.of(2))));
            waiters(pid(blocker), 1);
            while (jdbc.queryForObject("SELECT clock_timestamp() < ?::timestamptz", Boolean.class, deadline.toString())) Thread.sleep(20);
            blocker.commit();
            assertEquals("deadline", assertInstanceOf(ActivityValidationException.class, outcome(command)).field());
        }
        assertCounts(course, 0, 0, 0, 0);
    }
    @Test void teacherRemovedDuringLockWaitIsReauthorizedAfterWait() throws Exception {
        String p = "revoked"; var course = setup(p);
        try (var pool = Executors.newSingleThreadExecutor(); var blocker = lockCourse(course)) {
            var command = pool.submit(() -> create(p, course, UUID.randomUUID(), input()));
            waiters(pid(blocker), 1);
            try (var remove = blocker.prepareStatement("DELETE FROM course_teacher_assignment WHERE course_id=?")) {
                remove.setObject(1, course); assertEquals(1, remove.executeUpdate());
            }
            blocker.commit();
            assertInstanceOf(CourseNotAccessible.class, outcome(command));
        }
        assertCounts(course, 0, 0, 0, 0);
    }
    @Test void publicProvisioningWaitsForCreationSnapshotAndCannotChangeFixedParticipants() throws Exception {
        String p = "snapshot"; var course = setup(p); String name = "gate_" + UUID.randomUUID().toString().replace("-", "");
        long advisory = 730031;
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.course_id='" + course
                + "'::uuid THEN PERFORM pg_advisory_xact_lock(" + advisory + "); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE INSERT ON grouping_activity FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try (var pool = Executors.newFixedThreadPool(2); var gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try (var statement = gate.createStatement()) { statement.execute("SELECT pg_advisory_xact_lock(" + advisory + ")"); }
            var creation = pool.submit(() -> create(p, course, UUID.randomUUID(), input()));
            int creatingPid = waiters(pid(gate), 1).getFirst();
            var provision = pool.submit(() -> provisioning.provision(new Roster(accounts(p),
                    List.of(seed(p, "-A", List.of(p + "-one", p + "-two", p + "-three"))))));
            var provisioningPids = waiters(creatingPid, 1);
            assertFalse(provisioningPids.contains(pid(gate)));
            gate.commit();
            assertInstanceOf(Workspace.class, outcome(creation));
            provision.get(10, TimeUnit.SECONDS);
        } finally {
            jdbc.execute("DROP TRIGGER " + name + " ON grouping_activity"); jdbc.execute("DROP FUNCTION " + name + "()");
        }
        assertCounts(course, 1, 2, 1, 1);
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM course_enrollment WHERE course_id=?", Integer.class, course));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM activity_participant p JOIN app_account a ON a.id=p.account_id WHERE a.username=?", Integer.class, p + "-three"));
    }
    @Test void failureAfterFirstGroupRollsBackActivitySnapshotGroupsAndReceiptThenSameKeyCanRetry() {
        String p = "rollback"; var course = setup(p); var key = UUID.randomUUID();
        var input = new CreateActivity("Atomic creation", Instant.now().plusSeconds(3600), List.of(2, 2));
        provisioning.provision(new Roster(accounts(p), List.of(seed(p, "-A", List.of(p + "-three")))));
        // A fourth synthetic student makes two groups valid, so failure occurs during persistence.
        var fourth = new SeedAccount(p + "-four", "synthetic-password", "STUDENT", p + "-004", "Four");
        var expanded = new java.util.ArrayList<>(accounts(p)); expanded.add(fourth);
        provisioning.provision(new Roster(expanded, List.of(seed(p, "-A", List.of(p + "-four")))));
        String name = "reject_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.ordinal=2 AND EXISTS (SELECT 1 FROM grouping_activity WHERE id=NEW.activity_id AND course_id='"
                + course + "'::uuid) THEN RAISE EXCEPTION 'synthetic persistence failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE INSERT ON activity_group FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try { assertThrows(org.springframework.dao.DataAccessException.class, () -> create(p, course, key, input)); }
        finally { jdbc.execute("DROP TRIGGER " + name + " ON activity_group"); jdbc.execute("DROP FUNCTION " + name + "()"); }
        assertCounts(course, 0, 0, 0, 0);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?", Integer.class, key));
        create(p, course, key, input);
        assertCounts(course, 1, 4, 2, 1);
    }
}
