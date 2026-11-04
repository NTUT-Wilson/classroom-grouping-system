package com.classroomgrouping.courses;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.*;
import com.classroomgrouping.courses.api.CourseAnnouncements.*;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=ClassroomGroupingApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AnnouncementIntegrationTest {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    private static final boolean NATIVE=Boolean.getBoolean("announcement.test.native");
    private static String url,user,password;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if(NATIVE) {
            url=required("announcement.test.jdbc.url");user=required("announcement.test.jdbc.user");password=required("announcement.test.jdbc.password");
            var address=URI.create(url.substring("jdbc:".length()));
            if(!Boolean.getBoolean("announcement.test.disposable") || !("127.0.0.1".equals(address.getHost())||"localhost".equals(address.getHost()))
                    ||address.getPort()<1||address.getPort()==5432||address.getPort()==5433||!address.getPath().startsWith("/v2_announcement_disposable_"))
                throw new IllegalArgumentException("Supplemental native tests require an explicitly disposable announcement database on a dedicated loopback port");
            System.out.println("SUPPLEMENTAL NATIVE POSTGRESQL MODE; does not clear the Docker/Testcontainers gate");
        } else {POSTGRES.start();url=POSTGRES.getJdbcUrl();user=POSTGRES.getUsername();password=POSTGRES.getPassword();}
        registry.add("spring.datasource.url",()->url); registry.add("spring.datasource.username",()->user); registry.add("spring.datasource.password",()->password);
    }
    @org.junit.jupiter.api.AfterAll static void stopDatabase(){if(!NATIVE)POSTGRES.stop();}
    private static String required(String property){String value=System.getProperty(property);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+property);return value;}
    @Autowired CourseAnnouncements announcements;
    @Autowired AnnouncementOutbox outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @LocalServerPort int port;
    record Fixture(UUID course, UUID second, String teacher, String coTeacher, String student, String outsider) {}
    private Fixture setup() {
        String p = "notice-" + UUID.randomUUID().toString().substring(0,8); UUID course=UUID.randomUUID(), second=UUID.randomUUID();
        String teacher=p+"t", co=p+"c", student=p+"s", outsider=p+"o";
        for (String user : List.of(teacher,co,student,outsider)) {
            UUID id=UUID.randomUUID(); boolean teaching=user.equals(teacher)||user.equals(co);
            jdbc.update("INSERT INTO app_account(id,username,password_hash,role) VALUES (?,?,?,?)",id,user,"{noop}synthetic-password",teaching?"TEACHER":"STUDENT");
            if (!teaching) jdbc.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,?,?)",id,user,"Synthetic");
        }
        jdbc.update("INSERT INTO course(id,code,name) VALUES (?,?,?),(?,?,?)",course,p,"No activity",second,p+"2","Second");
        for (UUID c : List.of(course,second)) for (String user:List.of(teacher,co)) jdbc.update("INSERT INTO course_teacher_assignment(course_id,account_id) SELECT ?,id FROM app_account WHERE username=?",c,user);
        jdbc.update("INSERT INTO course_enrollment(course_id,account_id) SELECT ?,id FROM app_account WHERE username=?",course,student);
        return new Fixture(course,second,teacher,co,student,outsider);
    }
    private Acknowledgement create(Fixture f) { return announcements.create(f::teacher,f.course,UUID.randomUUID(),"Original","Body <script>plain</script>\r\n😀"); }
    private long count(String table, UUID course) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE course_id=?",Long.class,course); }
    private List<?> snapshot(UUID course) {
        return List.of(jdbc.queryForList("SELECT * FROM course_announcement WHERE course_id=? ORDER BY id",course), jdbc.queryForList("SELECT * FROM course_announcement_catalog WHERE course_id=?",course),
                jdbc.queryForList("SELECT * FROM course_announcement_receipt WHERE course_id=? ORDER BY command,command_key",course),jdbc.queryForList("SELECT * FROM course_announcement_outbox WHERE course_id=? ORDER BY event_id",course));
    }
    @Test void coTeachersManageAndLateEnrolleesReadWithoutActivityWhileDeletesClearAllContent() {
        var f=setup(); var key=UUID.randomUUID(); var created=announcements.create(f::teacher,f.course,key,"Original","Private body");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_activity WHERE course_id=?",Integer.class,f.course));
        assertThrows(CourseNotAccessible.class,()->announcements.detail(f::outsider,f.course,created.id()));
        jdbc.update("INSERT INTO course_enrollment(course_id,account_id) SELECT ?,id FROM app_account WHERE username=?",f.course,f.outsider);
        assertEquals("Private body",announcements.detail(f::outsider,f.course,created.id()).body());
        var edited=announcements.edit(f::coTeacher,f.course,created.id(),UUID.randomUUID(),1L,"Edited","Second body");
        assertEquals(2,edited.revision());
        assertThrows(AnnouncementConflict.class,()->announcements.delete(f::teacher,f.course,created.id(),UUID.randomUUID(),1L));
        var deleteKey=UUID.randomUUID(); var deleted=announcements.delete(f::coTeacher,f.course,created.id(),deleteKey,2L);
        assertTrue(deleted.deleted()); assertEquals(3,deleted.revision());
        assertTrue(announcements.list(f::student,f.course,20,null).items().isEmpty());
        assertThrows(AnnouncementNotAvailable.class,()->announcements.detail(f::teacher,f.course,created.id()));
        assertNull(jdbc.queryForMap("SELECT title,body FROM course_announcement WHERE id=?",created.id()).get("title"));
        assertNull(jdbc.queryForMap("SELECT title,body FROM course_announcement WHERE id=?",created.id()).get("body"));
        assertEquals(new Status(true,deleted),announcements.status(f::coTeacher,f.course,deleteKey,Command.DELETE));
        assertEquals(new Status(false,null),announcements.status(f::teacher,f.course,deleteKey,Command.DELETE));
        assertEquals(deleted,announcements.delete(f::coTeacher,f.course,created.id(),deleteKey,2L));
        assertEquals(created,announcements.create(f::teacher,f.course,key,"Original","Private body"));
        assertEquals(3,count("course_announcement_receipt",f.course)); assertEquals(1,count("course_announcement_outbox",f.course));
        assertEquals("DELETE",outbox.pending(1000).stream().filter(e->e.sourceId().equals(created.id())).findFirst().orElseThrow().changeKind());
        assertFalse(jdbc.queryForList("SELECT * FROM course_announcement_receipt WHERE course_id=?",f.course).toString().contains("Private body"));
        assertFalse(jdbc.queryForList("SELECT * FROM course_announcement_outbox WHERE course_id=?",f.course).toString().contains("Second body"));
        jdbc.update("DELETE FROM course_teacher_assignment WHERE course_id=? AND account_id=(SELECT id FROM app_account WHERE username=?)",f.course,f.teacher);
        assertThrows(CourseNotAccessible.class,()->announcements.create(f::teacher,f.course,key,"Original","Private body"));
        assertThrows(CourseNotAccessible.class,()->announcements.status(f::teacher,f.course,key,Command.CREATE));
        assertThrows(CourseNotAccessible.class,()->announcements.detail(f::teacher,f.course,created.id()));
        assertThrows(CourseNotAccessible.class,()->announcements.list(f::teacher,f.course,20,null));
    }
    @Test void exactFingerprintsBindEveryInputAndCourseTargetRevision() {
        var f=setup(); var key=UUID.randomUUID(); var a=announcements.create(f::teacher,f.course,key,"Title","Body"); var before=snapshot(f.course);
        assertEquals(a,announcements.create(f::teacher,f.course,key,"Title","Body"));
        assertEquals("COMMAND_KEY_REUSED",assertThrows(AnnouncementConflict.class,()->announcements.create(f::teacher,f.course,key,"Title ","Body")).code());
        assertThrows(AnnouncementConflict.class,()->announcements.create(f::teacher,f.second,key,"Title","Body"));
        assertEquals(before,snapshot(f.course)); assertEquals(0,count("course_announcement",f.second));
        var b=create(f); UUID editKey=UUID.randomUUID(); announcements.edit(f::teacher,f.course,a.id(),editKey,1L,"New","Body");
        assertThrows(AnnouncementConflict.class,()->announcements.edit(f::teacher,f.course,b.id(),editKey,1L,"New","Body"));
        assertThrows(AnnouncementConflict.class,()->announcements.edit(f::teacher,f.course,a.id(),editKey,2L,"New","Body"));
        assertThrows(CourseNotAccessible.class,()->announcements.create(f::student,f.course,UUID.randomUUID(),"Title","Body"));
    }
    @Test void codePointValidationAndDatabaseConstraintsAgreeAndPreserveExactText() {
        var f=setup(); String title="😀".repeat(150), body="😀".repeat(10000); var a=announcements.create(f::teacher,f.course,UUID.randomUUID(),title,body);
        assertEquals(body,announcements.detail(f::student,f.course,a.id()).body());
        assertEquals(150,jdbc.queryForObject("SELECT char_length(title) FROM course_announcement WHERE id=?",Integer.class,a.id()));
        for(String invalid:List.of("", " \t\n", "\u0085\u00a0\ufeff", "\u001c", "\ud800", "\udc00", "a\u0000b", "😀".repeat(151)))
            assertThrows(AnnouncementValidationException.class,()->announcements.create(f::teacher,f.course,UUID.randomUUID(),invalid,"Body"));
        assertThrows(AnnouncementValidationException.class,()->announcements.create(f::teacher,f.course,UUID.randomUUID(),"Title","😀".repeat(10001)));
        for(String invalid:List.of("", " \t\n", "\u0085\u00a0\ufeff", "\u001c", "😀".repeat(151)))
            assertThrows(DataAccessException.class,()->jdbc.update("UPDATE course_announcement SET title=? WHERE id=?",invalid,a.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE course_announcement SET deleted_at=clock_timestamp() WHERE id=?",a.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE course_announcement SET title=NULL WHERE id=?",a.id()));
    }
    @Test void keysetPaginationScopesActorCourseAndUsesImmutablePublicationAndIdTieBreak() {
        var f=setup(); for(int i=0;i<7;i++)create(f);
        jdbc.update("UPDATE course_announcement SET published_at='2026-10-30T00:00:00Z' WHERE course_id=?",f.course);
        var ids=jdbc.queryForList("SELECT id FROM course_announcement WHERE course_id=? ORDER BY id DESC",UUID.class,f.course);
        var first=announcements.list(f::teacher,f.course,2,null); assertEquals(ids.subList(0,2),first.items().stream().map(Detail::id).toList());
        announcements.edit(f::coTeacher,f.course,ids.getLast(),UUID.randomUUID(),1L,"Edit retains order","Body");
        var actual=new ArrayList<UUID>(); actual.addAll(first.items().stream().map(Detail::id).toList()); String cursor=first.nextCursor();
        while(cursor!=null) { var page=announcements.list(f::teacher,f.course,2,cursor); actual.addAll(page.items().stream().map(Detail::id).toList());cursor=page.nextCursor(); }
        assertEquals(ids,actual);
        assertThrows(AnnouncementValidationException.class,()->announcements.list(f::coTeacher,f.course,2,first.nextCursor()));
        assertThrows(AnnouncementValidationException.class,()->announcements.list(f::teacher,f.second,2,first.nextCursor()));
        assertThrows(AnnouncementValidationException.class,()->announcements.list(f::teacher,f.course,51,null));
        assertThrows(AnnouncementValidationException.class,()->announcements.list(f::teacher,f.course,20,"bad"));
        UUID actor=jdbc.queryForObject("SELECT id FROM app_account WHERE username=?",UUID.class,f.teacher);
        String farCursor=Base64.getUrlEncoder().withoutPadding().encodeToString((actor+"|"+f.course+"|+999999999-01-01T00:00:00Z|"+ids.getFirst()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(AnnouncementValidationException.class,()->announcements.list(f::teacher,f.course,20,farCursor));
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=?",f.course);
        assertThrows(CourseNotAccessible.class,()->announcements.list(f::student,f.course,20,null));
    }
    @Test void lateOutboxFailureRollsBackSourceCatalogReceiptAndCompactionTogether() {
        var f=setup(); var a=create(f); var before=snapshot(f.course);
        jdbc.execute("CREATE FUNCTION reject_notice_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic late failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_notice_event BEFORE INSERT ON course_announcement_outbox FOR EACH ROW EXECUTE FUNCTION reject_notice_event()");
        try {
            assertThrows(DataAccessException.class,()->announcements.edit(f::teacher,f.course,a.id(),UUID.randomUUID(),1L,"Lost","Lost")); assertEquals(before,snapshot(f.course));
            assertThrows(DataAccessException.class,()->announcements.delete(f::teacher,f.course,a.id(),UUID.randomUUID(),1L)); assertEquals(before,snapshot(f.course));
            assertThrows(DataAccessException.class,()->create(f)); assertEquals(before,snapshot(f.course));
        } finally { jdbc.execute("DROP TRIGGER reject_notice_event ON course_announcement_outbox");jdbc.execute("DROP FUNCTION reject_notice_event()"); }
    }
    private Connection lock(UUID course) throws Exception {
        var c=dataSource.getConnection(); c.setAutoCommit(false);
        try(var s=c.prepareStatement("SELECT id FROM course WHERE id=? FOR UPDATE")) {s.setObject(1,course);try(var r=s.executeQuery()){assertTrue(r.next());}}
        return c;
    }
    private int pid(Connection c) throws Exception {try(var s=c.createStatement();var r=s.executeQuery("SELECT pg_backend_pid()")){r.next();return r.getInt(1);}}
    private void waiters(int pid,int count) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<until) {
            int found=jdbc.queryForObject("WITH RECURSIVE w(pid) AS (SELECT pid FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)) UNION SELECT a.pid FROM pg_stat_activity a JOIN w ON w.pid=ANY(pg_blocking_pids(a.pid))) SELECT count(*) FROM w",Integer.class,pid);
            if(found>=count)return; Thread.sleep(20);
        }
        fail("Expected observed independent PostgreSQL lock waiters");
    }
    private Object outcome(Future<?> f) throws Exception {try{return f.get(15,TimeUnit.SECONDS);}catch(ExecutionException error){return error.getCause();}}
    @Test void concurrentSameKeyCreateAndRevisionEditRacesHaveSingleLogicalWinners() throws Exception {
        var f=setup(); var key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f.course)) {
            var one=pool.submit(()->announcements.create(f::teacher,f.course,key,"Title","Body"));
            var two=pool.submit(()->announcements.create(f::teacher,f.course,key,"Title","Body"));
            waiters(pid(gate),2);gate.commit(); assertEquals(outcome(one),outcome(two));
        }
        assertEquals(1,count("course_announcement",f.course)); var a=announcements.list(f::teacher,f.course,20,null).items().getFirst();
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f.course)) {
            var one=pool.submit(()->announcements.edit(f::teacher,f.course,a.id(),UUID.randomUUID(),1L,"One","Body"));
            var two=pool.submit(()->announcements.edit(f::coTeacher,f.course,a.id(),UUID.randomUUID(),1L,"Two","Body"));
            waiters(pid(gate),2);gate.commit();var results=List.of(outcome(one),outcome(two));
            assertEquals(1,results.stream().filter(Acknowledgement.class::isInstance).count()); assertEquals(1,results.stream().filter(AnnouncementConflict.class::isInstance).count());
        }
        assertEquals(2,count("course_announcement_receipt",f.course));assertEquals(2,announcements.detail(f::teacher,f.course,a.id()).revision());
    }
    @Test void bothEditDeleteOrdersPreserveWinnerAndRejectStaleCommands() throws Exception {
        for(boolean editFirst:List.of(true,false)) {
            var f=setup();var a=create(f);
            try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f.course)) {
                Supplier<Acknowledgement> edit=()->announcements.edit(f::teacher,f.course,a.id(),UUID.randomUUID(),1L,"Winner","Body");
                Supplier<Acknowledgement> delete=()->announcements.delete(f::coTeacher,f.course,a.id(),UUID.randomUUID(),1L);
                var first=pool.submit(()->(editFirst?edit:delete).get());waiters(pid(gate),1);
                var second=pool.submit(()->(editFirst?delete:edit).get());waiters(pid(gate),2);gate.commit();
                assertInstanceOf(Acknowledgement.class,outcome(first));Object rejected=outcome(second);
                assertTrue(rejected instanceof AnnouncementConflict||rejected instanceof AnnouncementNotAvailable);
            }
            assertEquals(2,count("course_announcement_receipt",f.course));
            if(editFirst)assertEquals("Winner",announcements.detail(f::teacher,f.course,a.id()).title());else assertThrows(AnnouncementNotAvailable.class,()->announcements.detail(f::teacher,f.course,a.id()));
        }
    }
    @Test void revocationDuringObservedCourseLockWaitDeniesWritesReplayStatusAndReads() throws Exception {
        for(String operation:List.of("create","replay","status","detail","page")) {
            var f=setup();var key=UUID.randomUUID();var a=announcements.create(f::teacher,f.course,key,"Title","Body");var before=snapshot(f.course);
            try(var pool=Executors.newSingleThreadExecutor();var gate=lock(f.course)) {
                var pending=pool.submit(()->switch(operation) {
                    case "create"->announcements.create(f::teacher,f.course,UUID.randomUUID(),"Other","Body");
                    case "replay"->announcements.create(f::teacher,f.course,key,"Title","Body");
                    case "status"->announcements.status(f::teacher,f.course,key,Command.CREATE);
                    case "detail"->announcements.detail(f::teacher,f.course,a.id());
                    default->announcements.list(f::teacher,f.course,20,null);
                });waiters(pid(gate),1);
                try(var s=gate.prepareStatement("DELETE FROM course_teacher_assignment WHERE course_id=? AND account_id=(SELECT id FROM app_account WHERE username=?)")){s.setObject(1,f.course);s.setString(2,f.teacher);assertEquals(1,s.executeUpdate());}
                gate.commit();assertInstanceOf(CourseNotAccessible.class,outcome(pending));
            }
            assertEquals(before,snapshot(f.course));
        }
    }
    @Test void crossCourseUniqueReceiptWaitReauthorizesAfterTeacherRevocation() throws Exception {
        var f=setup();var key=UUID.randomUUID();var beforeB=snapshot(f.second);
        var ownerPid=new CompletableFuture<Integer>();var releaseOwner=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                var ack=announcements.create(f::teacher,f.course,key,"Course A","Original A body");
                ownerPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                try {assertTrue(releaseOwner.await(30,TimeUnit.SECONDS),"Release uncommitted receipt owner");}
                catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                return ack;
            }));
            try {
                int blocker=ownerPid.get(15,TimeUnit.SECONDS);
                var second=pool.submit(()->announcements.create(f::teacher,f.second,key,"Course B","Different B body"));
                waiters(blocker,1);
                // The two commands hold different course locks: B must specifically wait on A's unique receipt.
                var receiptWaiters=jdbc.queryForList("SELECT pid FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)) AND wait_event_type='Lock' AND wait_event='transactionid' AND query LIKE '%INSERT INTO course_announcement_receipt%'",Integer.class,blocker);
                assertEquals(1,receiptWaiters.size());assertNotEquals(blocker,receiptWaiters.getFirst());
                // Controlled fixture revocation bypasses course serialization to exercise the second authorization check.
                jdbc.update("DELETE FROM course_teacher_assignment WHERE course_id=? AND account_id=(SELECT id FROM app_account WHERE username=?)",f.second,f.teacher);
                releaseOwner.countDown();
                var committed=assertInstanceOf(Acknowledgement.class,outcome(first));
                assertInstanceOf(CourseNotAccessible.class,outcome(second));
                assertEquals(new Status(true,committed),announcements.status(f::teacher,f.course,key,Command.CREATE));
                assertEquals(beforeB,snapshot(f.second));
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM course_announcement_receipt WHERE command='CREATE' AND command_key=?",Integer.class,key));
                System.out.println("ANNOUNCEMENT_RACE cross-course unique receipt transaction wait observed; revoked B denied without mutation");
            } finally {releaseOwner.countDown();}
        }
    }
    @Test void studentDetailAndEveryListPageReauthorizeEnrollmentAfterCourseLockWait() throws Exception {
        for(String operation:List.of("detail","page","nextPage")) {
            var f=setup();var a=create(f);create(f);var cursor=announcements.list(f::student,f.course,1,null).nextCursor();assertNotNull(cursor);
            var before=snapshot(f.course);
            try(var pool=Executors.newSingleThreadExecutor();var gate=lock(f.course)) {
                var pending=pool.submit(()->operation.equals("detail")?announcements.detail(f::student,f.course,a.id()):announcements.list(f::student,f.course,1,operation.equals("nextPage")?cursor:null));
                waiters(pid(gate),1);
                try(var statement=gate.prepareStatement("DELETE FROM course_enrollment WHERE course_id=? AND account_id=(SELECT id FROM app_account WHERE username=?)")) {
                    statement.setObject(1,f.course);statement.setString(2,f.student);assertEquals(1,statement.executeUpdate());
                }
                gate.commit();assertInstanceOf(CourseNotAccessible.class,outcome(pending));
            }
            assertEquals(before,snapshot(f.course));
            System.out.println("ANNOUNCEMENT_RACE student "+operation+" course lock wait observed; revoked enrollment denied");
        }
    }
    @Test void separateMaintenanceProcessSerializesWithDeletionAndConditionalAcknowledgement() throws Exception {
        for(boolean maintenanceFirst:List.of(true,false)) {
            var f=setup();var a=create(f);announcements.edit(f::teacher,f.course,a.id(),UUID.randomUUID(),1L,"Current","Current body");
            // Reconstitute one superseded metadata event to model a backlog accumulated before housekeeping.
            jdbc.update("INSERT INTO course_announcement_outbox(event_id,course_id,source_id,revision,change_kind) VALUES (?,?,?,1,'CREATE')",UUID.randomUUID(),f.course,a.id());
            var currentEvent=outbox.pending(1000).stream().filter(e->e.sourceId().equals(a.id())&&e.revision()==2).findFirst().orElseThrow();
            var receiptsBefore=jdbc.queryForList("SELECT * FROM course_announcement_receipt WHERE course_id=? ORDER BY command,command_key",f.course);
            UUID deleteKey=UUID.randomUUID();Process maintenance=null;
            try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f.course)) {
                try {
                    Future<Acknowledgement> deletion;
                    if(maintenanceFirst) {
                        maintenance=startMaintenance(1000);waiters(pid(gate),1);
                        deletion=pool.submit(()->announcements.delete(f::teacher,f.course,a.id(),deleteKey,2L));
                    } else {
                        deletion=pool.submit(()->announcements.delete(f::teacher,f.course,a.id(),deleteKey,2L));waiters(pid(gate),1);
                        maintenance=startMaintenance(1000);
                    }
                    waiters(pid(gate),2);
                    var acknowledgement=pool.submit(()->outbox.acknowledge(currentEvent.eventId(),2));
                    waiters(pid(gate),3);gate.commit();
                    var deleted=assertInstanceOf(Acknowledgement.class,outcome(deletion));assertTrue(deleted.deleted());assertEquals(3,deleted.revision());
                    assertEquals(Boolean.FALSE,outcome(acknowledgement),"A queued acknowledgement cannot acknowledge the superseding deletion");
                    assertTrue(maintenance.waitFor(20,TimeUnit.SECONDS),"Independent non-web maintenance must finish");
                    String output=new String(maintenance.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertEquals(0,maintenance.exitValue(),output);
                    assertTrue(output.contains("superseded events removed="),output);assertFalse(output.contains("Tomcat"));
                    var retained=jdbc.queryForList("SELECT revision,change_kind,acknowledged_at FROM course_announcement_outbox WHERE source_id=?",a.id());
                    assertEquals(1,retained.size());assertEquals(3L,retained.getFirst().get("revision"));assertEquals("DELETE",retained.getFirst().get("change_kind"));assertNull(retained.getFirst().get("acknowledged_at"));
                    var source=jdbc.queryForMap("SELECT title,body,revision,deleted_at FROM course_announcement WHERE id=?",a.id());assertNull(source.get("title"));assertNull(source.get("body"));assertNotNull(source.get("deleted_at"));assertEquals(3L,source.get("revision"));
                    assertEquals(3,count("course_announcement_receipt",f.course));
                    for(var receipt:receiptsBefore)assertEquals(receipt,jdbc.queryForMap("SELECT * FROM course_announcement_receipt WHERE actor_id=? AND command=? AND command_key=?",receipt.get("actor_id"),receipt.get("command"),receipt.get("command_key")));
                    assertEquals(new Status(true,deleted),announcements.status(f::teacher,f.course,deleteKey,Command.DELETE));
                    System.out.println("ANNOUNCEMENT_RACE maintenanceFirst="+maintenanceFirst+"; three independent PostgreSQL course lock waiters observed; latest deletion retained unacknowledged");
                } finally {
                    gate.rollback();
                    if(maintenance!=null&&maintenance.isAlive()) {maintenance.destroyForcibly();maintenance.waitFor(5,TimeUnit.SECONDS);}
                }
            }
        }
    }
    @Test void dormantEditingStorageIsBoundedPerSourceAndConditionalAckCompactionRacesAreSafe() throws Exception {
        var f=setup();var a=create(f);UUID first=outbox.pending(1000).stream().filter(e->e.sourceId().equals(a.id())).findFirst().orElseThrow().eventId();
        for(int i=1;i<=1000;i++)announcements.edit(f::teacher,f.course,a.id(),UUID.randomUUID(),(long)i,"Edit "+i,"Body");
        assertEquals(1,count("course_announcement_outbox",f.course));assertEquals(1001,count("course_announcement_receipt",f.course));assertFalse(outbox.acknowledge(first,1));
        var latest=outbox.pending(1000).stream().filter(e->e.sourceId().equals(a.id())).findFirst().orElseThrow();
        try(var pool=Executors.newFixedThreadPool(2);var gate=lock(f.course)) {
            var ack=pool.submit(()->outbox.acknowledge(latest.eventId(),latest.revision()));
            var deletion=pool.submit(()->announcements.delete(f::teacher,f.course,a.id(),UUID.randomUUID(),1001L));
            waiters(pid(gate),2);gate.commit();assertInstanceOf(Boolean.class,outcome(ack));assertInstanceOf(Acknowledgement.class,outcome(deletion));
        }
        var deletion=outbox.pending(1000).stream().filter(e->e.sourceId().equals(a.id())).findFirst().orElseThrow();assertEquals("DELETE",deletion.changeKind());assertEquals(1002,deletion.revision());
        assertFalse(outbox.acknowledge(deletion.eventId(),1001));assertTrue(outbox.acknowledge(deletion.eventId(),1002));assertFalse(outbox.acknowledge(deletion.eventId(),1002));
        assertEquals(1,count("course_announcement_outbox",f.course));
        for(int i=0;i<100;i++) {var source=create(f);if(i%2==0)announcements.delete(f::teacher,f.course,source.id(),UUID.randomUUID(),1L);}
        assertEquals(101,count("course_announcement",f.course));assertEquals(101,count("course_announcement_outbox",f.course));
        for(String table:List.of("course_announcement","course_announcement_receipt","course_announcement_outbox")) {
            Long bytes=jdbc.queryForObject("SELECT sum(pg_column_size(t)) FROM "+table+" t WHERE course_id=?",Long.class,f.course);assertTrue(bytes>0);
            Long relationBytes=jdbc.queryForObject("SELECT pg_total_relation_size(?::regclass)",Long.class,table);
            Long heapBytes=jdbc.queryForObject("SELECT pg_relation_size(?::regclass)",Long.class,table),indexBytes=jdbc.queryForObject("SELECT pg_indexes_size(?::regclass)",Long.class,table);
            System.out.println("ANNOUNCEMENT_STORAGE "+table+" rows="+count(table,f.course)+" row_bytes="+bytes+" heap_bytes="+heapBytes+" index_bytes="+indexBytes+" total_relation_bytes="+relationBytes+" [physical sizes include all test fixtures]");
        }
        for(var metric:Map.of("active_sources","course_announcement t WHERE t.deleted_at IS NULL","deleted_sources","course_announcement t WHERE t.deleted_at IS NOT NULL",
                "pending_events","course_announcement_outbox t WHERE t.acknowledged_at IS NULL","acknowledged_events","course_announcement_outbox t WHERE t.acknowledged_at IS NOT NULL",
                "superseded_events","course_announcement_outbox t WHERE EXISTS(SELECT 1 FROM course_announcement_outbox n WHERE n.source_id=t.source_id AND n.revision>t.revision)").entrySet()) {
            var measurement=jdbc.queryForMap("SELECT count(*) AS rows,coalesce(sum(pg_column_size(t)),0) AS bytes FROM "+metric.getValue()+" AND t.course_id=?",f.course);
            System.out.println("ANNOUNCEMENT_STORAGE "+metric.getKey()+" "+measurement);
        }
    }
    private HttpClient client() {return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    private HttpResponse<String> request(HttpClient client,String path,String method,String body,String token,String type) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)builder.header("X-CSRF-TOKEN",token);if(type!=null)builder.header("Content-Type",type);
        return client.send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(HttpClient client,String path) throws Exception {return request(client,path,"GET","",null,null);}
    private String csrf(HttpClient client) throws Exception {return mapper.readTree(get(client,"/api/auth/csrf").body()).get("token").asString();}
    private HttpClient login(String username) throws Exception {
        var client=client();assertEquals(204,request(client,"/api/auth/login","POST","username="+username+"&password=synthetic-password",csrf(client),"application/x-www-form-urlencoded").statusCode());return client;
    }
    @Test void httpStrictDtosPrivateReadsCsrfSessionAndDeniedRoutes() throws Exception {
        var f=setup();String base="/api/courses/"+f.course+"/announcements";var anonymous=client();
        assertEquals(401,get(anonymous,base).statusCode());var teacher=login(f.teacher);var student=login(f.student);var outsider=login(f.outsider);
        String key=UUID.randomUUID().toString(),valid=mapper.writeValueAsString(Map.of("commandKey",key,"title","<script>Title</script>","body","# Plain **text**\nBody"));
        assertEquals(403,request(teacher,base,"POST",valid,null,"application/json").statusCode());
        assertEquals(403,request(student,base,"POST",valid,csrf(student),"application/json").statusCode());
        assertEquals(404,get(outsider,base).statusCode());
        for(String body:List.of("null","[]","{", "{}","{\"commandKey\":7,\"title\":\"T\",\"body\":\"B\"}",valid.substring(0,valid.length()-1)+",\"extra\":1}","{\"commandKey\":\""+key+"\",\"title\":3,\"body\":\"B\"}")) {
            var result=request(teacher,base,"POST",body,csrf(teacher),"application/json");assertEquals(400,result.statusCode(),body+" -> "+result.body());
            var code=mapper.readTree(result.body()).get("code");assertNotNull(code,body+" -> "+result.body());assertEquals("INVALID_ANNOUNCEMENT",code.asString());
        }
        var saved=request(teacher,base,"POST",valid,csrf(teacher),"application/json");assertEquals(200,saved.statusCode());
        var ack=mapper.readTree(saved.body());assertEquals(Set.of("id","revision","deleted"),fieldNames(ack));String id=ack.get("id").asString();
        var detail=get(student,base+"/"+id);assertEquals(200,detail.statusCode());assertTrue(detail.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(Set.of("id","courseId","title","body","publishedAt","updatedAt","revision"),fieldNames(mapper.readTree(detail.body())));
        assertEquals("<script>Title</script>",mapper.readTree(detail.body()).get("title").asString());
        for(String limit:List.of("bad","0","51","1.5","-1","999999999999")) {var bad=get(teacher,base+"?limit="+limit);assertEquals(400,bad.statusCode());assertEquals("INVALID_ANNOUNCEMENT",mapper.readTree(bad.body()).get("code").asString());}
        for(String revision:List.of("\"1\"","1.0","true","null","-1","9223372036854775808")) {
            var bad=request(teacher,base+"/"+id+"/delete","POST","{\"commandKey\":\""+UUID.randomUUID()+"\",\"expectedRevision\":"+revision+"}",csrf(teacher),"application/json");assertEquals(400,bad.statusCode());
        }
        assertEquals(400,get(teacher,"/api/courses/"+f.course+"/announcement-commands/"+key+"?command=OTHER").statusCode());
        assertEquals(403,get(student,"/api/courses/"+f.course+"/announcement-commands/"+key+"?command=CREATE").statusCode());
        assertEquals(403,request(teacher,"/internal/announcement-maintenance","POST","",csrf(teacher),null).statusCode());
        assertEquals(403,request(teacher,base+"/maintenance","POST","",csrf(teacher),null).statusCode());
        assertEquals(204,request(teacher,"/api/auth/logout","POST","",csrf(teacher),null).statusCode());assertEquals(401,get(teacher,base).statusCode());
    }
    private Set<String> fieldNames(tools.jackson.databind.JsonNode node) {var names=new HashSet<String>();node.properties().forEach(entry->names.add(entry.getKey()));return names;}
    @Test void populatedV10UpgradePreservesGroupingHistoryAndImmutablePlan() {
        String schema="notice_upgrade_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(url,user,password);
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("10").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password));
        UUID teacher=UUID.randomUUID(),one=UUID.randomUUID(),two=UUID.randomUUID(),course=UUID.randomUUID(),activity=UUID.randomUUID(),group=UUID.randomUUID();
        db.update("INSERT INTO app_account VALUES (?, 'upgrade-teacher','preserved','TEACHER'),(?, 'upgrade-one','preserved','STUDENT'),(?, 'upgrade-two','preserved','STUDENT')",teacher,one,two);
        db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?, 'upgrade1','One'),(?, 'upgrade2','Two')",one,two);
        db.update("INSERT INTO course VALUES (?, 'UPGRADE-NOTICE','Preserved')",course);
        db.update("INSERT INTO course_teacher_assignment(course_id,account_id) VALUES (?,?)",course,teacher);
        db.update("INSERT INTO course_enrollment VALUES (?,?),(?,?)",course,one,course,two);
        db.update("INSERT INTO course_introduction VALUES (?,?,'Preserved introduction')",course,one);
        db.update("INSERT INTO grouping_activity(id,course_id,instructions,deadline) VALUES (?,?,'Preserved instructions',clock_timestamp()-interval '1 day')",activity,course);
        db.update("INSERT INTO activity_participant VALUES (?,?),(?,?)",activity,one,activity,two);
        db.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,1,2)",group,activity);
        db.update("INSERT INTO activity_membership VALUES (?,?,?)",activity,one,group);
        db.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",one,group);
        db.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'REJECTED')",UUID.randomUUID(),activity,group,two,one);
        db.update("INSERT INTO grouping_command_receipt VALUES (?,'JOIN',?,'preserved-digest',?)",one,UUID.randomUUID(),activity);
        db.update("INSERT INTO allocation_plan(activity_id,state,placement_count) VALUES (?,'COMPLETE',0)",activity);
        var tables=List.of("app_account","student_identity","course","course_teacher_assignment","course_enrollment","course_introduction","grouping_activity","activity_participant","activity_group","activity_membership","application_attempt","grouping_command_receipt","allocation_plan","allocation_placement");
        var before=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,db.queryForList("SELECT * FROM "+table));
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table),table);
        assertEquals(11,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM course_announcement",Integer.class));
        assertThrows(DataAccessException.class,()->db.update("UPDATE allocation_plan SET placement_count=1 WHERE activity_id=?",activity));
    }
    @Test void explicitMaintenanceIsNonWebBoundedContentFreeAndPreservesLatestDeletion() throws Exception {
        var f=setup();var a=create(f);announcements.delete(f::teacher,f.course,a.id(),UUID.randomUUID(),1L);
        // Simulate a dormant pre-housekeeping backlog using metadata only; current source/tombstone is authoritative.
        jdbc.update("UPDATE course_announcement SET revision=1102 WHERE id=?",a.id());
        jdbc.update("DELETE FROM course_announcement_outbox WHERE source_id=?",a.id());
        jdbc.update("INSERT INTO course_announcement_outbox(event_id,course_id,source_id,revision,change_kind) SELECT md5(? || value::text)::uuid,?,?,value,CASE WHEN value=1102 THEN 'DELETE' ELSE 'EDIT' END FROM generate_series(1,1102) value",a.id().toString(),f.course,a.id());
        var sourceBefore=jdbc.queryForList("SELECT * FROM course_announcement WHERE course_id=?",f.course);
        long receipts=count("course_announcement_receipt",f.course);
        var first=maintenance(1);assertEquals(0,first.exitValue());String output=new String(first.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(output.contains("superseded events removed=1000"),output);assertFalse(output.contains("Tomcat"));assertFalse(output.contains("Private"));assertFalse(output.contains("synthetic-password"));
        assertEquals(102,count("course_announcement_outbox",f.course));
        var second=maintenance(1);assertEquals(0,second.exitValue());assertEquals(1,count("course_announcement_outbox",f.course));
        assertEquals(sourceBefore,jdbc.queryForList("SELECT * FROM course_announcement WHERE course_id=?",f.course));assertEquals(receipts,count("course_announcement_receipt",f.course));
        var event=outbox.pending(1000).stream().filter(e->e.sourceId().equals(a.id())).findFirst().orElseThrow();assertEquals(1102,event.revision());assertEquals("DELETE",event.changeKind());
        assertNull(jdbc.queryForMap("SELECT title,body FROM course_announcement WHERE id=?",a.id()).get("body"));
    }
    @Test void conditionalAcknowledgementJoinsOuterTransactionAndRollsBackDurableHandoffFailure() {
        var f=setup();var a=create(f);var event=outbox.pending(1000).stream().filter(e->e.sourceId().equals(a.id())).findFirst().orElseThrow();
        var template=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class,()->template.execute(status->{assertTrue(outbox.acknowledge(event.eventId(),1));throw new IllegalStateException("synthetic handoff failure");}));
        assertTrue(outbox.pending(1000).stream().anyMatch(e->e.eventId().equals(event.eventId())));
        assertTrue(outbox.acknowledge(event.eventId(),1));assertFalse(outbox.pending(1000).stream().anyMatch(e->e.eventId().equals(event.eventId())));
        assertEquals("Original",announcements.detail(f::teacher,f.course,a.id()).title());
    }
    @Test void freshApplicationContextRecoversReceiptAndLatestDeletionIntentWithoutAConsumer() {
        var f=setup();UUID createKey=UUID.randomUUID(),deleteKey=UUID.randomUUID();
        var created=announcements.create(f::teacher,f.course,createKey,"Durable title","Durable body");
        var deleted=announcements.delete(f::teacher,f.course,created.id(),deleteKey,1L);var before=snapshot(f.course);
        try(var restarted=new org.springframework.boot.builder.SpringApplicationBuilder(ClassroomGroupingApplication.class).run(
                "--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=local",
                "--spring.datasource.url="+url,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--app.demo.enabled=false","--app.roster.enabled=false","--app.batch-fixture.enabled=false","--app.scheduler.enabled=false")) {
            var restored=restarted.getBean(CourseAnnouncements.class);var restoredOutbox=restarted.getBean(AnnouncementOutbox.class);
            assertEquals(new Status(true,deleted),restored.status(f::teacher,f.course,deleteKey,Command.DELETE));
            assertEquals(created,restored.create(f::teacher,f.course,createKey,"Durable title","Durable body"));
            assertThrows(AnnouncementNotAvailable.class,()->restored.detail(f::student,f.course,created.id()));
            var pending=restoredOutbox.pending(1000).stream().filter(e->e.sourceId().equals(created.id())).findFirst().orElseThrow();
            assertEquals("DELETE",pending.changeKind());assertEquals(2,pending.revision());
        }
        assertEquals(before,snapshot(f.course));
    }
    private Process startMaintenance(int courses) throws Exception {
        var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),"com.classroomgrouping.courses.internal.AnnouncementMaintenance","--compact",Integer.toString(courses));
        builder.redirectErrorStream(true);builder.environment().put("SPRING_DATASOURCE_URL",url);builder.environment().put("SPRING_DATASOURCE_USERNAME",user);builder.environment().put("SPRING_DATASOURCE_PASSWORD",password);
        // These must have no effect: this entry point cannot scan Spring/demo/scheduler components.
        builder.environment().put("APP_DEMO_ENABLED","true");builder.environment().put("APP_ROSTER_ENABLED","true");builder.environment().put("SERVER_PORT",Integer.toString(port));
        return builder.start();
    }
    private Process maintenance(int courses) throws Exception {
        var process=startMaintenance(courses);
        if(!process.waitFor(20,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Maintenance must finish without a web listener");}
        return process;
    }
}
