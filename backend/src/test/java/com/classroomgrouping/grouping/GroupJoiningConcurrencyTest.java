package com.classroomgrouping.grouping;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.grouping.api.GroupingConflict;
import com.classroomgrouping.grouping.api.GroupingActivities;
import com.classroomgrouping.grouping.api.GroupingActivities.CreateActivity;
import com.classroomgrouping.grouping.api.GroupingMemberships;
import com.classroomgrouping.grouping.api.GroupingMemberships.Discovery;
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
class GroupJoiningConcurrencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired GroupingActivities activities;
    @Autowired GroupingMemberships memberships;
    @Autowired RosterProvisioning provisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    private UUID setup(String p, int seconds) {
        provisioning.provision(new Roster(List.of(
                new SeedAccount(p, "synthetic-password", "TEACHER", null, null),
                new SeedAccount(p + "1", "synthetic-password", "STUDENT", p + "001", "One"),
                new SeedAccount(p + "2", "synthetic-password", "STUDENT", p + "002", "Two"),
                new SeedAccount(p + "3", "synthetic-password", "STUDENT", p + "003", "Three"),
                new SeedAccount(p + "4", "synthetic-password", "STUDENT", p + "004", "Four")),
                List.of(new SeedCourse(p, "Joining " + p, List.of(p), List.of(p + "1", p + "2", p + "3", p + "4")))));
        UUID course = jdbc.queryForObject("SELECT id FROM course WHERE code=?", UUID.class, p);
        var deadline = jdbc.queryForObject("SELECT clock_timestamp() + make_interval(secs => ?)", OffsetDateTime.class, seconds).toInstant();
        activities.create(() -> p, course, UUID.randomUUID(), new CreateActivity("Join deliberately", deadline, List.of(2, 2)));
        return course;
    }
    private Discovery read(String actor, UUID course) {
        return memberships.discover(() -> actor, course);
    }
    private Discovery join(String actor, UUID course, UUID group, UUID key) {
        return memberships.joinEmpty(() -> actor, course, group, key);
    }
    private void oneMemberOneManager(UUID course) {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM activity_membership m JOIN grouping_activity a ON a.id=m.activity_id WHERE a.course_id=?", Integer.class, course));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM activity_group g JOIN grouping_activity a ON a.id=g.activity_id WHERE a.course_id=? AND g.manager_account_id IS NOT NULL", Integer.class, course));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM activity_group g JOIN activity_membership m ON m.activity_id=g.activity_id AND m.group_id=g.id AND m.account_id=g.manager_account_id JOIN grouping_activity a ON a.id=g.activity_id WHERE a.course_id=?", Integer.class, course));
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
    private Discovery transaction(Supplier<Discovery> action) {
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
    private Object outcome(Future<Discovery> future) throws Exception {
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
        return List.of(jdbc.queryForList("SELECT m.* FROM activity_membership m JOIN grouping_activity a ON a.id=m.activity_id WHERE a.course_id=? ORDER BY m.account_id", course),
                jdbc.queryForList("SELECT * FROM grouping_activity WHERE course_id=?", course),
                jdbc.queryForList("SELECT p.* FROM activity_participant p JOIN grouping_activity a ON a.id=p.activity_id WHERE a.course_id=? ORDER BY p.account_id", course),
                jdbc.queryForList("SELECT g.* FROM activity_group g JOIN grouping_activity a ON a.id=g.activity_id WHERE a.course_id=? ORDER BY g.ordinal", course),
                jdbc.queryForList("SELECT r.* FROM grouping_command_receipt r JOIN grouping_activity a ON a.id=r.activity_id WHERE a.course_id=? ORDER BY r.command,r.command_key", course));
    }
    @Test void sameEmptyGroupHasExactlyOneManagerMemberAndPreciseLosingOutcome() throws Exception {
        String p = "join-group"; var course = setup(p, 3600); var group = read(p + "1", course).groups().getFirst().id();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var first = pool.submit(() -> transaction(() -> join(p + "1", course, group, UUID.randomUUID())));
            awaitWaiters(pid(gate), 1);
            var second = pool.submit(() -> transaction(() -> join(p + "2", course, group, UUID.randomUUID())));
            awaitWaiters(pid(gate), 2); gate.commit();
            var outcomes = List.of(outcome(first), outcome(second));
            assertEquals(1, outcomes.stream().filter(Discovery.class::isInstance).count());
            var failure = outcomes.stream().filter(GroupingConflict.class::isInstance).map(GroupingConflict.class::cast).findFirst().orElseThrow();
            assertEquals("GROUP_NOT_EMPTY", failure.code()); assertEquals("This group is no longer empty", failure.getMessage());
        }
        oneMemberOneManager(course);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt r JOIN grouping_activity a ON a.id=r.activity_id WHERE a.course_id=?", Integer.class, course));
    }
    @Test void sameStudentRacingDifferentEmptyGroupsCannotSwitchOrAcquireTwoMemberships() throws Exception {
        String p = "join-student"; var course = setup(p, 3600); var groups = read(p + "1", course).groups();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var first = pool.submit(() -> transaction(() -> join(p + "1", course, groups.get(0).id(), UUID.randomUUID())));
            var second = pool.submit(() -> transaction(() -> join(p + "1", course, groups.get(1).id(), UUID.randomUUID())));
            awaitWaiters(pid(gate), 2); gate.commit();
            var outcomes = List.of(outcome(first), outcome(second));
            assertEquals(1, outcomes.stream().filter(Discovery.class::isInstance).count());
            assertEquals("ALREADY_GROUPED", outcomes.stream().filter(GroupingConflict.class::isInstance).map(GroupingConflict.class::cast).findFirst().orElseThrow().code());
        }
        oneMemberOneManager(course);
    }
    @Test void duplicateKeySerializesOnceAndChangedGroupReuseCannotExecute() throws Exception {
        String p = "join-key"; var course = setup(p, 3600); var groups = read(p + "1", course).groups(); var key = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var first = pool.submit(() -> transaction(() -> join(p + "1", course, groups.getFirst().id(), key)));
            var second = pool.submit(() -> transaction(() -> join(p + "1", course, groups.getFirst().id(), key)));
            awaitWaiters(pid(gate), 2); gate.commit();
            assertEquals(assertInstanceOf(Discovery.class, outcome(first)),
                    assertInstanceOf(Discovery.class, outcome(second)));
        }
        var before = snapshot(course);
        assertEquals("COMMAND_KEY_REUSED", assertThrows(GroupingConflict.class, () -> join(p + "1", course, groups.get(1).id(), key)).code());
        assertEquals(before, snapshot(course)); oneMemberOneManager(course);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?", Integer.class, key));
    }
    @Test void actualActivityLockWaitCrossingCutoffRollsBackEveryJoinWrite() throws Exception {
        String p = "join-cutoff"; var course = setup(p, 3); var group = read(p + "1", course).groups().getFirst().id();
        var deadline = activities.workspace(() -> p, course).activity().deadline(); var before = snapshot(course);
        try (var pool = Executors.newSingleThreadExecutor(); var gate = lock(course)) {
            var result = pool.submit(() -> transaction(() -> join(p + "1", course, group, UUID.randomUUID())));
            awaitWaiters(pid(gate), 1); after(deadline); gate.commit();
            assertEquals("ACTIVITY_CLOSED", assertInstanceOf(GroupingConflict.class, outcome(result)).code());
        }
        assertEquals(before, snapshot(course));
    }
    @Test void enrollmentRevokedWhileWaitingRejectsNewJoinAndCommittedReplay() throws Exception {
        String p = "join-revoked"; var course = setup(p, 3600); var groups = read(p + "1", course).groups(); var key = UUID.randomUUID();
        join(p + "1", course, groups.getFirst().id(), key); var before = snapshot(course);
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(course)) {
            var replay = pool.submit(() -> transaction(() -> join(p + "1", course, groups.getFirst().id(), key)));
            var fresh = pool.submit(() -> transaction(() -> join(p + "2", course, groups.get(1).id(), UUID.randomUUID())));
            awaitWaiters(pid(gate), 2);
            try (var remove = gate.prepareStatement("DELETE FROM course_enrollment WHERE course_id=?")) {
                remove.setObject(1, course); assertEquals(4, remove.executeUpdate());
            }
            gate.commit();
            assertInstanceOf(CourseNotAccessible.class, outcome(replay)); assertInstanceOf(CourseNotAccessible.class, outcome(fresh));
        }
        assertEquals(before, snapshot(course));
    }
    @Test void persistenceFailureRollsBackMembershipManagerAndReceiptAndSameKeyCanRetry() {
        String p = "join-rollback"; var course = setup(p, 3600); var group = read(p + "1", course).groups().getFirst().id();
        var before = snapshot(course); var key = UUID.randomUUID();
        String name = "reject_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='" + group
                + "'::uuid AND NEW.manager_account_id IS NOT NULL THEN RAISE EXCEPTION 'synthetic joining failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + name + " AFTER UPDATE ON activity_group FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> join(p + "1", course, group, key));
            assertEquals(before, snapshot(course));
        } finally { jdbc.execute("DROP TRIGGER " + name + " ON activity_group"); jdbc.execute("DROP FUNCTION " + name + "()"); }
        assertEquals(group, join(p + "1", course, group, key).ownGroupId()); oneMemberOneManager(course);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?", Integer.class, key));
    }
    @Test void readsAndSuccessfulReplayAfterRealCutoffExposeNoMembershipsAndNeverMutate() throws Exception {
        String p = "join-private"; var course = setup(p, 3); var group = read(p + "1", course).groups().getFirst().id(); var key = UUID.randomUUID();
        join(p + "1", course, group, key); var deadline = activities.workspace(() -> p, course).activity().deadline(); after(deadline);
        var before = snapshot(course);
        for (var projection : List.of(read(p + "1", course), read(p + "2", course), join(p + "1", course, group, key))) {
            assertEquals("FINALIZING", projection.status()); assertNull(projection.ownGroupId()); assertTrue(projection.groups().isEmpty());
        }
        assertEquals(before, snapshot(course));
        assertEquals("ACTIVITY_CLOSED", assertThrows(GroupingConflict.class, () -> join(p + "2", course, group, UUID.randomUUID())).code());
        assertEquals(before, snapshot(course));
    }
}

