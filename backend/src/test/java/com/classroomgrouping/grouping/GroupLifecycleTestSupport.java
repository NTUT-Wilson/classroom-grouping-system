package com.classroomgrouping.grouping;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.grouping.api.*;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class GroupLifecycleTestSupport {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RosterProvisioning provisioning;
    @Autowired GroupingActivities activities;
    @Autowired GroupingMemberships memberships;
    @Autowired GroupingApplications applications;
    @Autowired GroupingInvitations invitations;
    @Autowired DueWork dueWork;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    record Fixture(String prefix, UUID course, UUID activity, List<UUID> groups, List<UUID> students) {
        String user(int i) { return prefix + "-s" + i; }
        String teacher() { return prefix + "-t"; }
        UUID group() { return groups.getFirst(); }
    }
    Fixture fixture(String prefix) {
        var accounts = new ArrayList<SeedAccount>();
        accounts.add(new SeedAccount(prefix + "-t", "synthetic-password", "TEACHER", null, null));
        var names = new ArrayList<String>();
        for (int i = 0; i < 6; i++) {
            String user = prefix + "-s" + i; names.add(user);
            accounts.add(new SeedAccount(user, "synthetic-password", "STUDENT", user, "Synthetic " + i));
        }
        provisioning.provision(new Roster(accounts, List.of(new SeedCourse(prefix, "Lifecycle " + prefix, List.of(prefix + "-t"), names))));
        UUID course = jdbc.queryForObject("SELECT id FROM course WHERE code=?", UUID.class, prefix);
        var deadline = jdbc.queryForObject("SELECT clock_timestamp()+interval '1 hour'", OffsetDateTime.class).toInstant();
        var activity = activities.create(() -> prefix + "-t", course, UUID.randomUUID(),
                new GroupingActivities.CreateActivity("Lifecycle", deadline, List.of(4, 2))).activity();
        var students = names.stream().map(name -> jdbc.queryForObject("SELECT id FROM app_account WHERE username=?", UUID.class, name)).toList();
        return new Fixture(prefix, course, activity.id(), activity.groups().stream().map(GroupingActivities.Group::id).toList(), students);
    }
    GroupingMemberships.Discovery join(Fixture f, int i, UUID key) { return memberships.joinEmpty(() -> f.user(i), f.course(), f.group(), key); }
    GroupingMemberships.Discovery leave(Fixture f, int i, UUID key) { return memberships.leave(() -> f.user(i), f.course(), f.group(), key); }
    GroupingMemberships.Discovery transfer(Fixture f, int actor, int target, UUID key) {
        return memberships.transferManager(() -> f.user(actor), f.course(), f.group(), key, f.students().get(target));
    }
    void populate(Fixture f, int count) {
        join(f, 0, UUID.randomUUID());
        for (int i = 1; i < count; i++) {
            String username = f.user(i);
            var page = applications.apply(() -> username, f.course(), f.group(), UUID.randomUUID());
            UUID attempt = page.items().stream().filter(a -> a.status().equals("PENDING")).findFirst().orElseThrow().id();
            applications.decide(() -> f.user(0), f.course(), attempt, UUID.randomUUID(), "APPROVE");
        }
    }
    UUID manager(Fixture f) { return jdbc.queryForObject("SELECT manager_account_id FROM activity_group WHERE id=?", UUID.class, f.group()); }
    int occupancy(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?", Integer.class, f.activity(), f.group()); }
    long receipts(UUID key) { return jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?", Long.class, key); }
    List<?> snapshot(Fixture f) {
        var tables = List.of("grouping_activity", "activity_group", "activity_participant", "activity_membership", "application_attempt", "invitation_attempt", "grouping_command_receipt", "allocation_work", "allocation_plan", "allocation_placement");
        return tables.stream().map(table -> jdbc.queryForList("SELECT * FROM " + table + " WHERE "
                + (table.equals("grouping_activity") ? "id" : "activity_id") + "=? ORDER BY 1,2", f.activity())).toList();
    }
    void integrity(Fixture f) {
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM activity_group g WHERE g.activity_id=? AND
                ((SELECT count(*) FROM activity_membership m WHERE m.activity_id=g.activity_id AND m.group_id=g.id)>g.capacity
                OR (EXISTS(SELECT 1 FROM activity_membership m WHERE m.activity_id=g.activity_id AND m.group_id=g.id)
                    AND NOT EXISTS(SELECT 1 FROM activity_membership m WHERE m.activity_id=g.activity_id AND m.group_id=g.id AND m.account_id=g.manager_account_id)))
                """, Integer.class, f.activity()));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM activity_participant WHERE activity_id=?", Integer.class, f.activity()));
        assertEquals(List.of(4, 2), jdbc.queryForList("SELECT capacity FROM activity_group WHERE activity_id=? ORDER BY ordinal", Integer.class, f.activity()));
    }
    Connection lock(Fixture f) throws Exception {
        var c = dataSource.getConnection(); c.setAutoCommit(false);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, c.getTransactionIsolation());
        try (var s = c.prepareStatement("SELECT id FROM grouping_activity WHERE id=? FOR UPDATE")) {
            s.setObject(1, f.activity()); try (var r = s.executeQuery()) { assertTrue(r.next()); }
        }
        return c;
    }
    int pid(Connection c) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT pg_backend_pid()")) { assertTrue(r.next()); return r.getInt(1); }
    }
    Object transaction(Supplier<?> action) {
        var template = new TransactionTemplate(transactions); template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> { assertEquals("read committed", jdbc.queryForObject("SHOW transaction_isolation", String.class)); return action.get(); });
    }
    void awaitWaiters(int blocker, int count) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            var pids = jdbc.queryForList("""
                    WITH RECURSIVE waiting(pid) AS (
                      SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                      UNION SELECT a.pid FROM pg_stat_activity a JOIN waiting w ON w.pid = ANY(pg_blocking_pids(a.pid))
                    ) SELECT pid FROM waiting
                    """, Integer.class, blocker);
            if (pids.size() >= count) { assertFalse(pids.contains(blocker)); assertEquals(pids.size(), pids.stream().distinct().count()); return; }
            Thread.sleep(20);
        } while (System.nanoTime() < until);
        fail("Expected " + count + " PostgreSQL lock waiters for " + blocker);
    }
    Object outcome(Future<?> f) throws Exception {
        try { return f.get(10, TimeUnit.SECONDS); } catch (ExecutionException e) { return e.getCause(); }
    }
    void persistenceFailure(Fixture f, UUID key, Runnable action) {
        var before = snapshot(f); String name = "reject_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        // Failure after business mutation, at the receipt insert, proves the entire transaction rolls back.
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.command_key='" + key + "'::uuid THEN RAISE EXCEPTION 'synthetic receipt failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER " + name + " AFTER INSERT ON grouping_command_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try { assertThrows(org.springframework.dao.DataAccessException.class, action::run); assertEquals(before, snapshot(f)); }
        finally { jdbc.execute("DROP TRIGGER " + name + " ON grouping_command_receipt"); jdbc.execute("DROP FUNCTION " + name + "()"); }
        assertEquals(0, receipts(key));
    }
}
