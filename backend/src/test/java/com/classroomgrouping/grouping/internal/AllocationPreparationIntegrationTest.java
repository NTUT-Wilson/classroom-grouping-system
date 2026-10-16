package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.grouping.api.DueWork;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(classes=ClassroomGroupingApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false",
                "app.scheduler.secret=allocation-production-test-secret-32-plus"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AllocationPreparationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DueWork production;
    @LocalServerPort int port;

    private record Fixture(UUID activity,List<UUID> students,List<UUID> groups) {}
    private Fixture fixture(int count, boolean due) {
        UUID course=UUID.randomUUID(), activity=UUID.randomUUID();
        jdbc.update("INSERT INTO course(id,code,name) VALUES (?,?, 'Synthetic')",course,course.toString());
        jdbc.update("INSERT INTO grouping_activity(id,course_id,instructions,deadline) VALUES (?,?,'Synthetic',clock_timestamp()+make_interval(secs => ?))",activity,course,due?-60:3600);
        var students=new ArrayList<UUID>(); var groups=new ArrayList<UUID>();
        for(int i=0;i<count;i++) {
            UUID student=UUID.randomUUID(); students.add(student);
            jdbc.update("INSERT INTO app_account VALUES (?,?,'{noop}synthetic','STUDENT')",student,student.toString());
            jdbc.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,?,'Synthetic')",student,student.toString());
            jdbc.update("INSERT INTO activity_participant VALUES (?,?)",activity,student);
        }
        for(int i=0;i<count/2;i++) {
            UUID group=UUID.randomUUID(); groups.add(group);
            jdbc.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,?,2)",group,activity,i+1);
        }
        return new Fixture(activity,students,groups);
    }
    private void member(Fixture f,int student,int group,boolean manager) {
        new TransactionTemplate(transactions).executeWithoutResult(s->{
            jdbc.update("INSERT INTO activity_membership VALUES (?,?,?)",f.activity(),f.students().get(student),f.groups().get(group));
            if(manager)jdbc.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",f.students().get(student),f.groups().get(group));
        });
    }
    private int count(String table,UUID activity) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE activity_id=?",Integer.class,activity); }
    private String state(UUID activity) {return jdbc.queryForObject("SELECT state FROM allocation_work WHERE activity_id=?",String.class,activity);}
    private List<?> plan(UUID activity) {return jdbc.queryForList("SELECT * FROM allocation_placement WHERE activity_id=? ORDER BY account_id",activity);}
    private DatabaseDueWork worker() {return new DatabaseDueWork(jdbc,transactions);}
    private void retryNow(UUID activity) {jdbc.update("UPDATE allocation_work SET retry_at=clock_timestamp()-interval '1 second' WHERE activity_id=?",activity);}

    @Test void productionSchedulerAuthenticatesAndPreparesWithoutAssignments() throws Exception {
        var f=fixture(4,true); var future=fixture(2,false);
        assertInstanceOf(DatabaseDueWork.class,production);
        var client=HttpClient.newHttpClient(); var url=URI.create("http://127.0.0.1:"+port+"/internal/scheduler/tick");
        assertEquals(401,client.send(HttpRequest.newBuilder(url).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(0,count("allocation_plan",f.activity()));
        var req=HttpRequest.newBuilder(url).header("Authorization","Bearer allocation-production-test-secret-32-plus").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(204,client.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals("PREPARED",state(f.activity())); assertEquals(4,count("allocation_placement",f.activity()));
        assertEquals(List.of(2L,2L),jdbc.queryForList("SELECT count(*) FROM allocation_placement WHERE activity_id=? GROUP BY group_id ORDER BY count(*)",Long.class,f.activity()));
        assertEquals(0,count("activity_membership",f.activity())); assertEquals(0,count("allocation_plan",future.activity()));
    }

    @Test void onlyUngroupedArePlannedAndTerminalRequestsArePreserved() {
        var f=fixture(4,true); member(f,0,0,true);
        for(String table:List.of("application_attempt","invitation_attempt")) {
            String person=table.equals("application_attempt")?"applicant_account_id":"recipient_account_id";
            String manager=table.equals("application_attempt")?"manager_account_id":"sender_account_id";
            for(String status:List.of("PENDING","ACCEPTED","REJECTED","CANCELED","EXPIRED")) {
                jdbc.update("INSERT INTO "+table+"(id,activity_id,group_id,"+person+","+manager+",status,reason) VALUES (?,?,?,?,?,?,?)",
                        UUID.randomUUID(),f.activity(),f.groups().getFirst(),f.students().get(1),f.students().getFirst(),status,status.equals("CANCELED")?"Joined a group":null);
            }
            jdbc.update("INSERT INTO "+table+"(id,activity_id,group_id,"+person+","+manager+",status,reason) VALUES (?,?,?,?,?,'CANCELED','Group full')",
                    UUID.randomUUID(),f.activity(),f.groups().getFirst(),f.students().get(2),f.students().getFirst());
        }
        worker().processDueWork();
        assertEquals(3,count("allocation_placement",f.activity())); assertEquals(1,count("activity_membership",f.activity()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM allocation_placement WHERE activity_id=? AND account_id=?",Integer.class,f.activity(),f.students().getFirst()));
        for(String table:List.of("application_attempt","invitation_attempt")) {
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE activity_id=? AND status='PENDING'",Integer.class,f.activity()));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE activity_id=? AND status='EXPIRED'",Integer.class,f.activity()));
            for(String terminal:List.of("ACCEPTED","REJECTED")) assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE activity_id=? AND status=?",Integer.class,f.activity(),terminal));
            assertEquals(List.of("Group full","Joined a group"),jdbc.queryForList("SELECT reason FROM "+table+" WHERE activity_id=? AND status='CANCELED' ORDER BY reason",String.class,f.activity()));
        }
    }

    @Test void restartRepeatAndLostResponseReuseOneImmutablePlan() {
        var f=fixture(8,true); var lost=new DatabaseDueWork(jdbc,transactions,new DatabaseDueWork.PreparationObserver(){
            @Override public void afterCommit(UUID id){if(id.equals(f.activity()))throw new TransientDataAccessResourceException("synthetic lost acknowledgement");}
        });
        lost.processDueWork(); var saved=plan(f.activity());
        assertEquals("PREPARED",state(f.activity()));
        for(int i=0;i<3;i++)worker().processDueWork();
        assertEquals(saved,plan(f.activity())); assertEquals(1,count("allocation_plan",f.activity()));
        assertEquals(0,count("activity_membership",f.activity()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE allocation_plan SET placement_count=0 WHERE activity_id=?",f.activity()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE allocation_placement SET group_id=? WHERE activity_id=?",f.groups().getLast(),f.activity()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM allocation_plan WHERE activity_id=?",f.activity()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO allocation_plan VALUES (?,'PREPARING',0,clock_timestamp())",fixture(2,true).activity()));
    }

    @Test void alreadyGroupedActivityHasCompleteEmptyPlan() {
        var f=fixture(2,true); member(f,0,0,true);member(f,1,0,false);
        worker().processDueWork();assertEquals("PREPARED",state(f.activity()));
        assertEquals(1,count("allocation_plan",f.activity())); assertEquals(0,count("allocation_placement",f.activity()));
        assertEquals(2,count("activity_membership",f.activity()));worker().processDueWork();
        assertEquals(1,count("allocation_plan",f.activity()));
    }

    private org.springframework.context.ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(ClassroomGroupingApplication.class).run(
                "--server.port=0", "--spring.profiles.active=local", "--app.demo.enabled=false", "--app.roster.enabled=false",
                "--app.scheduler.enabled=true", "--app.scheduler.interval-ms=3600000",
                "--spring.datasource.url="+POSTGRES.getJdbcUrl(), "--spring.datasource.username="+POSTGRES.getUsername(),
                "--spring.datasource.password="+POSTGRES.getPassword());
    }
    @Test void actualApplicationStartupCatchesUpAndRestartRetainsPlanWithoutBrowserTraffic() {
        var f=fixture(4,true);List<?> saved;
        try(var first=startApplication()) {
            assertInstanceOf(DatabaseDueWork.class,first.getBean(DueWork.class));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertEquals("PREPARED",state(f.activity())));
            saved=plan(f.activity());
        }
        try(var restarted=startApplication()) {
            assertInstanceOf(DatabaseDueWork.class,restarted.getBean(DueWork.class));
            assertEquals(saved,plan(f.activity()));assertEquals(1,count("allocation_plan",f.activity()));
            assertEquals(0,count("activity_membership",f.activity()));
        }
    }

    @Test void rollbackRecordsIndependentBackoffThenRetriesWhileHealthyWorkContinues() {
        var broken=fixture(4,true); var healthy=fixture(4,true); var calls=new AtomicInteger();
        var failing=new DatabaseDueWork(jdbc,transactions,new DatabaseDueWork.PreparationObserver(){
            @Override public void beforeCommit(UUID id){if(id.equals(broken.activity())){calls.incrementAndGet();throw new TransientDataAccessResourceException("synthetic transient");}}
        });
        failing.processDueWork(); assertEquals("RETRY",state(broken.activity()));assertEquals("PREPARED",state(healthy.activity()));
        assertEquals(0,count("allocation_plan",broken.activity()));assertEquals(0,count("activity_membership",broken.activity()));
        assertEquals(1,jdbc.queryForObject("SELECT attempts FROM allocation_work WHERE activity_id=?",Integer.class,broken.activity()));
        assertTrue(jdbc.queryForObject("SELECT retry_at > clock_timestamp() FROM allocation_work WHERE activity_id=?",Boolean.class,broken.activity()));
        failing.processDueWork();assertEquals(1,calls.get());
        jdbc.update("UPDATE allocation_work SET attempts=100 WHERE activity_id=?",broken.activity());retryNow(broken.activity());failing.processDueWork();
        assertTrue(jdbc.queryForObject("SELECT retry_at-clock_timestamp() <= interval '3600 seconds' AND retry_at-clock_timestamp() > interval '3590 seconds' FROM allocation_work WHERE activity_id=?",Boolean.class,broken.activity()));
        retryNow(broken.activity());worker().processDueWork();assertEquals("PREPARED",state(broken.activity()));
    }

    @Test void integrityFaultHaltsAndDoesNotBlockHealthyActivity() {
        var broken=fixture(4,true);var healthy=fixture(4,true);
        jdbc.update("UPDATE activity_group SET capacity=3 WHERE id=?",broken.groups().getFirst());
        worker().processDueWork();assertEquals("HALTED",state(broken.activity()));assertEquals("PREPARED",state(healthy.activity()));
        assertEquals("INTEGRITY",jdbc.queryForObject("SELECT failure_class FROM allocation_work WHERE activity_id=?",String.class,broken.activity()));
        assertEquals(0,count("allocation_plan",broken.activity()));
        jdbc.update("UPDATE activity_group SET capacity=2 WHERE id=?",broken.groups().getFirst());worker().processDueWork();
        assertEquals("HALTED",state(broken.activity()));assertEquals(0,count("allocation_plan",broken.activity()));
    }

    @Test void missingManagerIsIntegrityFailureAndRetryAttemptsSaturate() {
        var invalid=fixture(2,true);member(invalid,0,0,false);worker().processDueWork();
        assertEquals("HALTED",state(invalid.activity()));assertEquals(0,count("allocation_plan",invalid.activity()));
        var transientFailure=fixture(2,true);
        jdbc.update("INSERT INTO allocation_work VALUES (?,'RETRY',2147483647,clock_timestamp()-interval '1 second','TRANSIENT')",transientFailure.activity());
        new DatabaseDueWork(jdbc,transactions,new DatabaseDueWork.PreparationObserver(){
            @Override public void beforeCommit(UUID id){if(id.equals(transientFailure.activity()))throw new TransientDataAccessResourceException("synthetic");}
        }).processDueWork();
        assertEquals(Integer.MAX_VALUE,jdbc.queryForObject("SELECT attempts FROM allocation_work WHERE activity_id=?",Integer.class,transientFailure.activity()));
        assertEquals("RETRY",state(transientFailure.activity()));
    }

    @Test void boundedBatchesDoNotStarveRemainingActivities() {
        var fixtures=new ArrayList<Fixture>();for(int i=0;i<65;i++)fixtures.add(fixture(2,true));
        worker().processDueWork();
        long first=fixtures.stream().filter(f->count("allocation_plan",f.activity())==1).count();
        assertTrue(first>0 && first<=64);
        worker().processDueWork();worker().processDueWork();
        assertTrue(fixtures.stream().allMatch(f->count("allocation_plan",f.activity())==1));
    }

    @Test void completeExistingPlanIsReconciledAndIncompleteCoverageHalts() {
        var good=fixture(4,true);worker().processDueWork();var saved=plan(good.activity());
        jdbc.update("DELETE FROM allocation_work WHERE activity_id=?",good.activity());worker().processDueWork();
        assertEquals(saved,plan(good.activity()));assertEquals("PREPARED",state(good.activity()));
        var invalid=fixture(4,true);
        jdbc.update("INSERT INTO allocation_plan(activity_id,state,placement_count) VALUES (?,'COMPLETE',0)",invalid.activity());
        worker().processDueWork();assertEquals("HALTED",state(invalid.activity()));
        assertEquals(1,count("allocation_plan",invalid.activity()));assertEquals(0,count("activity_membership",invalid.activity()));
    }

    private Connection lock(UUID activity) throws Exception {
        var connection=dataSource.getConnection(); connection.setAutoCommit(false);
        try(var s=connection.prepareStatement("SELECT id FROM grouping_activity WHERE id=? FOR UPDATE")){s.setObject(1,activity);try(var rs=s.executeQuery()){assertTrue(rs.next());}}
        return connection;
    }
    private int pid(Connection connection) throws Exception {
        try(var s=connection.createStatement();var rs=s.executeQuery("SELECT pg_backend_pid()")){assertTrue(rs.next());return rs.getInt(1);}
    }
    private void waiters(int pid,int count) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        do {
            int n=jdbc.queryForObject("WITH RECURSIVE w(pid) AS (SELECT pid FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)) UNION SELECT a.pid FROM pg_stat_activity a JOIN w ON w.pid=ANY(pg_blocking_pids(a.pid))) SELECT count(*) FROM w",Integer.class,pid);
            if(n>=count)return;Thread.sleep(20);
        }while(System.nanoTime()<limit);fail("Distinct database transactions did not wait for activity lock");
    }
    @Test void concurrentIndependentWorkersProduceExactlyOnePlan() throws Exception {
        var f=fixture(6,true);
        try(var held=lock(f.activity());var executor=Executors.newFixedThreadPool(2)) {
            int blocker=pid(held);var first=executor.submit(()->worker().processDueWork());var second=executor.submit(()->worker().processDueWork());
            waiters(blocker,2);held.commit();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,count("allocation_plan",f.activity()));assertEquals(6,count("allocation_placement",f.activity()));
        assertEquals(0,count("activity_membership",f.activity()));
    }

    @Test void concurrentPlacementCannotWaitForUncommittedHeaderThenAppendToCompletePlan() throws Exception {
        var f=fixture(2,true);
        try(var held=dataSource.getConnection();var executor=Executors.newSingleThreadExecutor()) {
            held.setAutoCommit(false);
            try(var s=held.prepareStatement("INSERT INTO allocation_plan(activity_id,state,placement_count) VALUES (?,'COMPLETE',0)")) {
                s.setObject(1,f.activity());s.executeUpdate();
            }
            var append=executor.submit(()->assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                    "INSERT INTO allocation_placement VALUES (?,?,?)",f.activity(),f.students().getFirst(),f.groups().getFirst())));
            try {append.get(3,TimeUnit.SECONDS);} finally {held.commit();}
        }
        assertEquals(0,count("allocation_placement",f.activity()));
    }

    @Test void workerRereadsCommittedExtensionAfterLockWait() throws Exception {
        var f=fixture(2,true);
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()+interval '1 second' WHERE id=?",f.activity());
        try(var held=lock(f.activity());var executor=Executors.newSingleThreadExecutor()) {
            try(var s=held.prepareStatement("UPDATE grouping_activity SET deadline=clock_timestamp()+interval '1 hour' WHERE id=?")){s.setObject(1,f.activity());s.executeUpdate();}
            long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(Boolean.TRUE.equals(jdbc.queryForObject("SELECT deadline>clock_timestamp() FROM grouping_activity WHERE id=?",Boolean.class,f.activity()))) {
                assertTrue(System.nanoTime()<limit);Thread.sleep(20);
            }
            var waiting=executor.submit(()->worker().processDueWork());waiters(pid(held),1);held.commit();waiting.get(10,TimeUnit.SECONDS);
        }
        assertEquals(0,count("allocation_plan",f.activity()));assertEquals(0,count("allocation_work",f.activity()));
    }
}
