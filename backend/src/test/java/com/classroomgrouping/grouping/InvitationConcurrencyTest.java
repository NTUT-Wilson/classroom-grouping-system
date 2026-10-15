package com.classroomgrouping.grouping;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.grouping.api.GroupingActivities;
import com.classroomgrouping.grouping.api.GroupingActivities.CreateActivity;
import com.classroomgrouping.grouping.api.GroupingInvitations;
import com.classroomgrouping.grouping.api.GroupingApplications;
import com.classroomgrouping.grouping.api.GroupingInvitations.InvitationPage;
import com.classroomgrouping.grouping.api.GroupingConflict;
import com.classroomgrouping.grouping.api.GroupingMemberships;
import com.classroomgrouping.grouping.api.GroupingValidationException;
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

/** PostgreSQL wait graphs prove that competing commands use distinct connections and transactions. */
@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, properties = {
        "spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvitationConcurrencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired GroupingActivities activities;
    @Autowired GroupingInvitations invitations;
    @Autowired GroupingApplications applications;
    @Autowired GroupingMemberships memberships;
    @Autowired RosterProvisioning provisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    private record Fixture(String prefix, UUID course, UUID first, UUID second) {}
    private Fixture setup(String prefix, int seconds) {
        return setup(prefix, seconds, true);
    }
    private Fixture setup(String prefix, int seconds, boolean secondManager) {
        provisioning.provision(new Roster(List.of(
                new SeedAccount(prefix, "synthetic-password", "TEACHER", null, null),
                new SeedAccount(prefix+"1", "synthetic-password", "STUDENT", prefix+"001", "Manager One"),
                new SeedAccount(prefix+"2", "synthetic-password", "STUDENT", prefix+"002", "Manager Two"),
                new SeedAccount(prefix+"3", "synthetic-password", "STUDENT", prefix+"003", "Applicant Three"),
                new SeedAccount(prefix+"4", "synthetic-password", "STUDENT", prefix+"004", "Applicant Four")),
                List.of(new SeedCourse(prefix, prefix, List.of(prefix), List.of(prefix+"1",prefix+"2",prefix+"3",prefix+"4")))));
        var course = jdbc.queryForObject("SELECT id FROM course WHERE code=?", UUID.class, prefix);
        var deadline = jdbc.queryForObject("SELECT clock_timestamp()+make_interval(secs=>?)", OffsetDateTime.class, seconds).toInstant();
        activities.create(() -> prefix, course, UUID.randomUUID(), new CreateActivity("Invitations", deadline, List.of(2,2)));
        var groups = memberships.discover(() -> prefix+"1", course).groups();
        memberships.joinEmpty(() -> prefix+"1", course, groups.get(0).id(), UUID.randomUUID());
        if (secondManager) memberships.joinEmpty(() -> prefix+"2", course, groups.get(1).id(), UUID.randomUUID());
        return new Fixture(prefix, course, groups.get(0).id(), groups.get(1).id());
    }
    private InvitationPage apply(Fixture f, int student, UUID group, UUID key) {
        return invitations.invite(() -> f.prefix+(group.equals(f.first)?1:2), f.course, group, jdbc.queryForObject("SELECT id FROM app_account WHERE username=?",UUID.class,f.prefix+student), key);
    }
    private UUID pending(Fixture f, int student, UUID group) {
        return apply(f,student,group,UUID.randomUUID()).items().stream()
                .filter(a -> a.groupId().equals(group) && a.recipient().accountId().equals(jdbc.queryForObject("SELECT id FROM app_account WHERE username=?",UUID.class,f.prefix+student)) && a.status().equals("PENDING")).findFirst().orElseThrow().id();
    }
    private InvitationPage decide(Fixture f, UUID attempt, UUID key, String decision) {
        return invitations.decide(() -> jdbc.queryForObject("SELECT a.username FROM invitation_attempt x JOIN app_account a ON a.id=x.recipient_account_id WHERE x.id=?",String.class,attempt),f.course,attempt,key,decision);
    }
    private Connection lock(Fixture f) throws Exception {
        var connection=dataSource.getConnection(); connection.setAutoCommit(false);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
        try(var statement=connection.prepareStatement("SELECT id FROM grouping_activity WHERE course_id=? FOR UPDATE")) {
            statement.setObject(1,f.course); try(var rows=statement.executeQuery()) { assertTrue(rows.next()); }
        }
        return connection;
    }
    private int pid(Connection connection) throws Exception {
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT pg_backend_pid()")) {
            assertTrue(rows.next());return rows.getInt(1);
        }
    }
    private <T> T transaction(Supplier<T> action) {
        var template=new TransactionTemplate(transactions);template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> { assertEquals("read committed",jdbc.queryForObject("SHOW transaction_isolation",String.class));return action.get(); });
    }
    private void waiters(Connection gate,int count) throws Exception {
        int blocker=pid(gate);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        do {
            var pids=jdbc.queryForList("""
                    WITH RECURSIVE waiting(pid) AS (
                      SELECT pid FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))
                      UNION SELECT a.pid FROM pg_stat_activity a JOIN waiting w ON w.pid=ANY(pg_blocking_pids(a.pid))
                    ) SELECT pid FROM waiting
                    """,Integer.class,blocker);
            if(pids.size()>=count) { assertFalse(pids.contains(blocker)); assertEquals(pids.size(),pids.stream().distinct().count()); return; }
            Thread.sleep(20);
        } while(System.nanoTime()<until);
        fail("Expected "+count+" distinct PostgreSQL lock waiters");
    }
    private Object outcome(Future<?> future) throws Exception {
        try { return future.get(10,TimeUnit.SECONDS); }
        catch(java.util.concurrent.ExecutionException failure) { return failure.getCause(); }
    }
    private void after(Instant deadline) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(Boolean.TRUE.equals(jdbc.queryForObject("SELECT clock_timestamp()<?::timestamptz",Boolean.class,deadline.toString()))) {
            assertTrue(System.nanoTime()<until);Thread.sleep(20);
        }
    }
    private List<?> snapshot(Fixture f) {
        return List.of(jdbc.queryForList("SELECT x.* FROM application_attempt x JOIN grouping_activity a ON a.id=x.activity_id WHERE a.course_id=? ORDER BY x.id",f.course),
                jdbc.queryForList("SELECT x.* FROM invitation_attempt x JOIN grouping_activity a ON a.id=x.activity_id WHERE a.course_id=? ORDER BY x.id",f.course),
                jdbc.queryForList("SELECT x.* FROM activity_membership x JOIN grouping_activity a ON a.id=x.activity_id WHERE a.course_id=? ORDER BY x.account_id",f.course),
                jdbc.queryForList("SELECT x.* FROM activity_group x JOIN grouping_activity a ON a.id=x.activity_id WHERE a.course_id=? ORDER BY x.id",f.course),
                jdbc.queryForList("SELECT x.* FROM grouping_command_receipt x JOIN grouping_activity a ON a.id=x.activity_id WHERE a.course_id=? ORDER BY x.command,x.command_key",f.course));
    }
    private void conflict(String code,Object result) { assertEquals(code,assertInstanceOf(GroupingConflict.class,result).code()); }

    @Test void concurrentReinvitationRetainsRejectedHistoryAndCounterpartRejectionsAreIndependent() throws Exception {
        var f=setup("inv-reinvite",3600);var rejected=pending(f,3,f.first);
        var application=applications.apply(() -> f.prefix+"3",f.course,f.first,UUID.randomUUID()).items().getFirst();
        decide(f,rejected,UUID.randomUUID(),"REJECT");
        assertEquals("PENDING",applications.history(() -> f.prefix+"3",f.course,null,20).items().getFirst().status());
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var one=pool.submit(() -> transaction(() -> apply(f,3,f.first,UUID.randomUUID())));
            var two=pool.submit(() -> transaction(() -> apply(f,3,f.first,UUID.randomUUID())));
            waiters(gate,2);gate.commit();var results=List.of(outcome(one),outcome(two));
            assertEquals(1,results.stream().filter(InvitationPage.class::isInstance).count());
            conflict("INVITATION_PENDING",results.stream().filter(GroupingConflict.class::isInstance).findFirst().orElseThrow());
        }
        applications.decide(() -> f.prefix+"1",f.course,application.id(),UUID.randomUUID(),"REJECT");
        assertEquals(List.of("PENDING","REJECTED"),invitations.history(() -> f.prefix+"3",f.course,null,20).items().stream().map(a -> a.status()).toList());
    }
    @Test void applicationApprovalAndInvitationAcceptanceCompeteForSameStudentAndForLastPlace() throws Exception {
        for(boolean sameStudent:List.of(true,false)) {
            var f=setup("inv-cross-"+sameStudent,3600);
            var application=applications.apply(() -> f.prefix+"3",f.course,f.first,UUID.randomUUID()).items().getFirst();
            var invitation=pending(f,sameStudent?3:4,f.first);
            try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
                var one=pool.submit(() -> transaction(() -> applications.decide(() -> f.prefix+"1",f.course,application.id(),UUID.randomUUID(),"APPROVE")));
                var two=pool.submit(() -> transaction(() -> decide(f,invitation,UUID.randomUUID(),"ACCEPT")));
                waiters(gate,2);gate.commit();var results=List.of(outcome(one),outcome(two));
                assertEquals(1,results.stream().filter(result -> !(result instanceof Throwable)).count());
                assertInstanceOf(GroupingConflict.class,results.stream().filter(Throwable.class::isInstance).findFirst().orElseThrow());
            }
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE group_id=?",Integer.class,f.first));
            var statuses=jdbc.queryForList("SELECT status FROM application_attempt WHERE id=? UNION ALL SELECT status FROM invitation_attempt WHERE id=?",String.class,application.id(),invitation);
            assertEquals(1,statuses.stream().filter("ACCEPTED"::equals).count());assertEquals(1,statuses.stream().filter("CANCELED"::equals).count());
            assertEquals(sameStudent?"Joined a group":"Group full",jdbc.queryForObject("SELECT reason FROM application_attempt WHERE id=? AND status='CANCELED' UNION ALL SELECT reason FROM invitation_attempt WHERE id=? AND status='CANCELED'",String.class,application.id(),invitation));
        }
    }
    @Test void sendingAndRecipientJoiningSerializeWithoutLeavingPendingInvitationOrChangingOtherActivity() throws Exception {
        var f=setup("inv-send-join",3600,false);var other=setup("inv-independent",3600);
        try(var pool=Executors.newFixedThreadPool(3);var gate=lock(f)) {
            var send=pool.submit(() -> transaction(() -> apply(f,3,f.first,UUID.randomUUID())));
            var join=pool.submit(() -> transaction(() -> memberships.joinEmpty(() -> f.prefix+"3",f.course,f.second,UUID.randomUUID())));
            waiters(gate,2);
            assertInstanceOf(InvitationPage.class,outcome(pool.submit(() -> transaction(() -> apply(other,3,other.first,UUID.randomUUID())))));
            gate.commit();var sendResult=outcome(send);assertFalse(outcome(join) instanceof Throwable);
            assertTrue(sendResult instanceof InvitationPage || sendResult instanceof GroupingConflict);
        }
        assertEquals(f.second,memberships.discover(() -> f.prefix+"3",f.course).ownGroupId());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM invitation_attempt WHERE group_id=? AND status='PENDING'",Integer.class,f.first));
        assertTrue(invitations.history(() -> f.prefix+"3",f.course,null,20).items().stream().allMatch(a -> "CANCELED".equals(a.status()) && "Joined a group".equals(a.reason())));
        assertEquals("PENDING",invitations.history(() -> other.prefix+"3",other.course,null,20).items().getFirst().status());
    }
    @Test void joiningPreservesSameActorsOutgoingInvitationAndAllOtherActivityHistory() {
        var f=setup("inv-outgoing",3600);var other=UUID.randomUUID();
        jdbc.update("INSERT INTO course(id,code,name) VALUES (?,?,?)",other,f.prefix+"-other","Other synthetic course");
        jdbc.update("INSERT INTO course_teacher_assignment(course_id,account_id) SELECT ?,account_id FROM course_teacher_assignment WHERE course_id=?",other,f.course);
        jdbc.update("INSERT INTO course_enrollment(course_id,account_id) SELECT ?,account_id FROM course_enrollment WHERE course_id=?",other,f.course);
        activities.create(() -> f.prefix,other,UUID.randomUUID(),new CreateActivity("Other activity",Instant.parse("2099-03-15T06:30:00Z"),List.of(2,2)));
        var otherGroup=memberships.discover(() -> f.prefix+"3",other).groups().getFirst().id();
        memberships.joinEmpty(() -> f.prefix+"3",other,otherGroup,UUID.randomUUID());
        invitations.invite(() -> f.prefix+"3",other,otherGroup,jdbc.queryForObject("SELECT id FROM app_account WHERE username=?",UUID.class,f.prefix+"4"),UUID.randomUUID());
        var otherFixture=new Fixture(f.prefix,other,otherGroup,null);var before=snapshot(otherFixture);
        decide(f,pending(f,3,f.first),UUID.randomUUID(),"ACCEPT");
        assertEquals(before,snapshot(otherFixture));
        assertEquals("PENDING",invitations.history(() -> f.prefix+"3",other,null,20).items().getFirst().status());
    }
    @Test void recipientCanRejectIndependentlyWhenSenderIsNoLongerEnrolled() {
        var f=setup("inv-independent-response",3600);var attempt=pending(f,3,f.first);
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=(SELECT id FROM app_account WHERE username=?)",f.course,f.prefix+"1");
        assertEquals("REJECTED",decide(f,attempt,UUID.randomUUID(),"REJECT").items().getFirst().status());
        assertNull(memberships.discover(() -> f.prefix+"3",f.course).ownGroupId());
    }
    @Test void duplicateSubmissionsSerializeWithoutReservingCapacityAndKeyReplayCannotChangeTarget() throws Exception {
        var f=setup("inv-duplicate",3600);var key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var first=pool.submit(() -> transaction(() -> apply(f,3,f.first,key)));
            var second=pool.submit(() -> transaction(() -> apply(f,3,f.first,key)));
            waiters(gate,2);gate.commit();
            assertEquals(assertInstanceOf(InvitationPage.class,outcome(first)),assertInstanceOf(InvitationPage.class,outcome(second)));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
        var before=snapshot(f);
        conflict("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,() -> apply(f,4,f.first,key)));
        assertEquals(before,snapshot(f));
        assertEquals(1,memberships.discover(() -> f.prefix+"3",f.course).groups().getFirst().availablePlaces());
        assertNull(memberships.discover(() -> f.prefix+"3",f.course).ownGroupId());
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var one=pool.submit(() -> transaction(() -> apply(f,4,f.first,UUID.randomUUID())));
            var two=pool.submit(() -> transaction(() -> apply(f,4,f.first,UUID.randomUUID())));
            waiters(gate,2);gate.commit();var results=List.of(outcome(one),outcome(two));
            assertEquals(1,results.stream().filter(InvitationPage.class::isInstance).count());
            conflict("INVITATION_PENDING",results.stream().filter(GroupingConflict.class::isInstance).findFirst().orElseThrow());
        }
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM invitation_attempt WHERE group_id=? AND status='PENDING'",Integer.class,f.first));
    }
    @Test void competingLastPlaceAcceptancesAcceptOneCancelTheOtherAndNeverOverfill() throws Exception {
        var f=setup("inv-last",3600);var one=pending(f,3,f.first);var two=pending(f,4,f.first);
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var first=pool.submit(() -> transaction(() -> decide(f,one,UUID.randomUUID(),"ACCEPT")));
            var second=pool.submit(() -> transaction(() -> decide(f,two,UUID.randomUUID(),"ACCEPT")));
            waiters(gate,2);gate.commit();var results=List.of(outcome(first),outcome(second));
            assertEquals(1,results.stream().filter(InvitationPage.class::isInstance).count());
            conflict("INVITATION_NOT_PENDING",results.stream().filter(GroupingConflict.class::isInstance).findFirst().orElseThrow());
        }
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE group_id=?",Integer.class,f.first));
        assertEquals(List.of("ACCEPTED","CANCELED"),jdbc.queryForList("SELECT status FROM invitation_attempt WHERE group_id=? ORDER BY status",String.class,f.first));
        assertEquals("Group full",jdbc.queryForObject("SELECT reason FROM invitation_attempt WHERE group_id=? AND status='CANCELED'",String.class,f.first));
    }
    @Test void acceptingInvitationsFromTwoGroupsEstablishesOneMembershipAndCancelsOtherAttempt() throws Exception {
        var f=setup("inv-two",3600);var one=pending(f,3,f.first);var two=pending(f,3,f.second);
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var first=pool.submit(() -> transaction(() -> decide(f,one,UUID.randomUUID(),"ACCEPT")));
            var second=pool.submit(() -> transaction(() -> decide(f,two,UUID.randomUUID(),"ACCEPT")));
            waiters(gate,2);gate.commit();var results=List.of(outcome(first),outcome(second));
            assertEquals(1,results.stream().filter(InvitationPage.class::isInstance).count());
            conflict("INVITATION_NOT_PENDING",results.stream().filter(GroupingConflict.class::isInstance).findFirst().orElseThrow());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_membership m JOIN app_account a ON a.id=m.account_id WHERE a.username=?",Integer.class,f.prefix+"3"));
        assertEquals("Joined a group",jdbc.queryForObject("SELECT reason FROM invitation_attempt WHERE id IN (?,?) AND status='CANCELED'",String.class,one,two));
    }
    @Test void sameDecisionKeyCommitsOnceAndChangingDecisionCannotReverseOutcome() throws Exception {
        var f=setup("inv-decision-key",3600);var attempt=pending(f,3,f.first);var key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var one=pool.submit(() -> transaction(() -> decide(f,attempt,key,"REJECT")));
            var two=pool.submit(() -> transaction(() -> decide(f,attempt,key,"REJECT")));
            waiters(gate,2);gate.commit();
            assertEquals(assertInstanceOf(InvitationPage.class,outcome(one)),assertInstanceOf(InvitationPage.class,outcome(two)));
        }
        var before=snapshot(f);
        conflict("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,() -> decide(f,attempt,key,"ACCEPT")));
        assertEquals(before,snapshot(f));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
    }
    @Test void failedReceiptInsertionRollsBackMembershipAcceptedAndBothKindsOfCancellation() {
        var f=setup("inv-rollback",3600);var accepted=pending(f,3,f.first);pending(f,3,f.second);pending(f,4,f.first);
        applications.apply(() -> f.prefix+"3",f.course,f.first,UUID.randomUUID());
        applications.apply(() -> f.prefix+"4",f.course,f.first,UUID.randomUUID());
        var before=snapshot(f);var key=UUID.randomUUID();String name="app_failure_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.command_key='"+key
                +"'::uuid AND (SELECT count(*) FROM activity_membership WHERE activity_id=NEW.activity_id)=3"
                +" AND (SELECT count(*) FROM invitation_attempt WHERE activity_id=NEW.activity_id AND status='ACCEPTED')=1"
                +" AND (SELECT count(*) FROM invitation_attempt WHERE activity_id=NEW.activity_id AND status='CANCELED')=2"
                +" AND (SELECT count(*) FROM application_attempt WHERE activity_id=NEW.activity_id AND status='CANCELED')=2"
                +" THEN RAISE EXCEPTION 'synthetic receipt failure after all writes'; END IF; RETURN NEW; END $$");
        // Defer the injected failure until commit, after membership and both cleanup updates ran.
        jdbc.execute("CREATE CONSTRAINT TRIGGER "+name+" AFTER INSERT ON grouping_command_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+name+"()");
        try {
            assertThrows(RuntimeException.class,() -> decide(f,accepted,key,"ACCEPT"));
            assertEquals(before,snapshot(f));
        } finally { jdbc.execute("DROP TRIGGER "+name+" ON grouping_command_receipt");jdbc.execute("DROP FUNCTION "+name+"()"); }
        decide(f,accepted,key,"ACCEPT");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
        assertEquals(List.of("ACCEPTED","CANCELED","CANCELED"),jdbc.queryForList("SELECT x.status FROM invitation_attempt x JOIN grouping_activity a ON a.id=x.activity_id WHERE a.course_id=? ORDER BY x.status",String.class,f.course));
    }
    @Test void emptyGroupJoiningCancelsOnlyPendingAttemptsAndRetainsRejectedHistory() {
        var f=setup("inv-empty-join",3600,false);var rejected=pending(f,3,f.first);
        decide(f,rejected,UUID.randomUUID(),"REJECT");var pending=pending(f,3,f.first);
        memberships.joinEmpty(() -> f.prefix+"3",f.course,f.second,UUID.randomUUID());
        var history=invitations.history(() -> f.prefix+"3",f.course,null,20);
        assertEquals("CANCELED",history.items().stream().filter(a -> a.id().equals(pending)).findFirst().orElseThrow().status());
        assertEquals("Joined a group",history.items().stream().filter(a -> a.id().equals(pending)).findFirst().orElseThrow().reason());
        assertEquals("REJECTED",history.items().stream().filter(a -> a.id().equals(rejected)).findFirst().orElseThrow().status());
        conflict("INVITATION_NOT_PENDING",assertThrows(GroupingConflict.class,() -> decide(f,pending,UUID.randomUUID(),"ACCEPT")));
        assertEquals(f.second,memberships.discover(() -> f.prefix+"3",f.course).ownGroupId());
    }
    @Test void waitingSubmissionApprovalAndRejectionAllRespectDatabaseCutoffAndReadsOnlyProjectExpiry() throws Exception {
        var f=setup("inv-cutoff",5);var attempt=pending(f,3,f.first);var before=snapshot(f);
        try(var pool=Executors.newFixedThreadPool(3);var gate=lock(f)) {
            var apply=pool.submit(() -> transaction(() -> apply(f,4,f.second,UUID.randomUUID())));
            var approve=pool.submit(() -> transaction(() -> decide(f,attempt,UUID.randomUUID(),"ACCEPT")));
            var reject=pool.submit(() -> transaction(() -> decide(f,attempt,UUID.randomUUID(),"REJECT")));
            waiters(gate,3);after(activities.workspace(() -> f.prefix,f.course).activity().deadline());gate.commit();
            conflict("ACTIVITY_CLOSED",outcome(apply));conflict("ACTIVITY_CLOSED",outcome(approve));conflict("ACTIVITY_CLOSED",outcome(reject));
        }
        assertEquals(before,snapshot(f));
        var history=invitations.history(() -> f.prefix+"3",f.course,null,20);
        assertEquals("EXPIRED",history.items().getFirst().status());
        assertEquals("FINALIZING",history.status());assertEquals(before,snapshot(f));
    }
    @Test void waitingNewCommandsAndReceiptReplayReauthorizeEnrollmentAfterLock() throws Exception {
        var f=setup("inv-revoked",3600);var key=UUID.randomUUID();apply(f,3,f.first,key);var before=snapshot(f);
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var replay=pool.submit(() -> transaction(() -> apply(f,3,f.first,key)));
            var fresh=pool.submit(() -> transaction(() -> apply(f,4,f.second,UUID.randomUUID())));
            waiters(gate,2);
            try(var remove=gate.prepareStatement("DELETE FROM course_enrollment WHERE course_id=?")) {
                remove.setObject(1,f.course);assertEquals(4,remove.executeUpdate());
            }
            gate.commit();assertInstanceOf(CourseNotAccessible.class,outcome(replay));assertInstanceOf(CourseNotAccessible.class,outcome(fresh));
        }
        assertEquals(before,snapshot(f));
    }
    @Test void waitingRecipientResponsesReauthorizeCurrentEnrollment() throws Exception {
        var f=setup("inv-decision-revoked",3600);var one=pending(f,3,f.first);var two=pending(f,4,f.second);
        var before=snapshot(f);
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f)) {
            var manager=pool.submit(() -> transaction(() -> decide(f,one,UUID.randomUUID(),"REJECT")));
            var applicant=pool.submit(() -> transaction(() -> decide(f,two,UUID.randomUUID(),"ACCEPT")));
            waiters(gate,2);
            try(var remove=gate.prepareStatement("DELETE FROM course_enrollment WHERE course_id=? AND account_id IN (SELECT id FROM app_account WHERE username IN (?,?))")) {
                remove.setObject(1,f.course);remove.setString(2,f.prefix+"3");remove.setString(3,f.prefix+"4");
                assertEquals(2,remove.executeUpdate());
            }
            gate.commit();assertInstanceOf(CourseNotAccessible.class,outcome(manager));assertInstanceOf(CourseNotAccessible.class,outcome(applicant));
        }
        assertEquals(before,snapshot(f));
    }
    @Test void rejectedReinvitationPreservesHistoryAndCursorPagesRemainActorAndCourseScoped() {
        var f=setup("inv-history",3600);var rejected=pending(f,3,f.first);
        decide(f,rejected,UUID.randomUUID(),"REJECT");var original=invitations.history(() -> f.prefix+"3",f.course,null,20).items().getFirst();
        var fresh=pending(f,3,f.first);assertNotEquals(rejected,fresh);
        var before=snapshot(f);var first=invitations.history(() -> f.prefix+"3",f.course,null,1);
        assertNotNull(first.nextCursor());var second=invitations.history(() -> f.prefix+"3",f.course,first.nextCursor(),1);
        assertEquals(1,second.items().size());assertNotEquals(first.items().getFirst().id(),second.items().getFirst().id());
        assertEquals(original,second.items().getFirst());
        assertThrows(GroupingValidationException.class,() -> invitations.history(() -> f.prefix+"4",f.course,first.nextCursor(),1));
        assertTrue(invitations.history(() -> f.prefix+"4",f.course,null,20).items().isEmpty());
        assertThrows(CourseNotAccessible.class,() -> invitations.history(() -> f.prefix,f.course,null,20));
        provisioning.provision(new Roster(List.of(
                new SeedAccount(f.prefix,"synthetic-password","TEACHER",null,null),
                new SeedAccount(f.prefix+"3","synthetic-password","STUDENT",f.prefix+"003","Applicant Three")),
                List.of(new SeedCourse(f.prefix+"-other","Other",List.of(f.prefix),List.of(f.prefix+"3")))));
        var other=jdbc.queryForObject("SELECT id FROM course WHERE code=?",UUID.class,f.prefix+"-other");
        assertThrows(GroupingValidationException.class,() -> invitations.history(() -> f.prefix+"3",other,first.nextCursor(),1));
        assertEquals(before,snapshot(f));
    }
}
