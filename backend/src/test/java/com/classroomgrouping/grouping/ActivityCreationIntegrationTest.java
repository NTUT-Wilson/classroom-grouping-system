package com.classroomgrouping.grouping;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.CourseWorkspace;
import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import com.classroomgrouping.grouping.api.*;
import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
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
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = ClassroomGroupingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "app.demo.enabled=false", "app.roster.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActivityCreationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RosterProvisioning provisioning;
    @Autowired CourseWorkspace courses;
    @Autowired GroupingActivities activities;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    private UUID setup(String prefix, int count) {
        var accounts = new ArrayList<SeedAccount>();
        accounts.add(new SeedAccount(prefix+"-teacher", "synthetic-password", "TEACHER", null, null));
        accounts.add(new SeedAccount(prefix+"-other", "synthetic-password", "TEACHER", null, null));
        var students = new ArrayList<String>();
        for (int i=0;i<count+1;i++) {
            String name=prefix+"-s"+i;
            accounts.add(new SeedAccount(name,"synthetic-password","STUDENT","000"+name,"Synthetic "+i));
            if(i<count) students.add(name);
        }
        provisioning.provision(new Roster(accounts,List.of(new SeedCourse(prefix,"Synthetic Course",List.of(prefix+"-teacher"),students))));
        return courses.list(()->prefix+"-teacher").getFirst().id();
    }
    private GroupingActivities.CreateActivity valid() { return new GroupingActivities.CreateActivity("  <plain> 陳 😀\r\n",Instant.now().plusSeconds(3600),List.of(2,2)); }
    private GroupingActivities.Workspace create(String prefix, UUID course, UUID key, GroupingActivities.CreateActivity request) {
        return activities.create(()->prefix+"-teacher",course,key,request);
    }
    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build(); }
    private HttpResponse<String> request(HttpClient client,String path,String method,String body,String token,String type) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)b.header("X-CSRF-TOKEN",token); if(type!=null)b.header("Content-Type",type);
        return client.send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(HttpClient client,String path) throws Exception {return request(client,path,"GET","",null,null);}
    private String csrf(HttpClient c) throws Exception {return JsonPath.read(get(c,"/api/auth/csrf").body(),"$.token");}
    private HttpClient login(String user) throws Exception {
        var c=client(); assertEquals(204,request(c,"/api/auth/login","POST","username="+user+"&password=synthetic-password",csrf(c),"application/x-www-form-urlencoded").statusCode());return c;
    }
    private String body(UUID key, GroupingActivities.CreateActivity r) {return mapper.writeValueAsString(Map.of("commandKey",key,"instructions",r.instructions(),"deadline",r.deadline().toString(),"capacities",r.capacities()));}
    private HttpResponse<String> post(HttpClient c,UUID course,String body) throws Exception {return request(c,"/api/courses/"+course+"/activity","POST",body,csrf(c),"application/json");}
    @Test void createsOrderedFixedSnapshotAndPrivacySafeProjectionWithReadOnlyCutoff() throws Exception {
        var course=setup("creation",4);
        assertEquals(4,activities.workspace(()->"creation-teacher",course).enrolledCount());
        assertNull(activities.workspace(()->"creation-s0",course).enrolledCount());
        var response=post(login("creation-teacher"),course,body(UUID.randomUUID(),valid()));
        assertEquals(200,response.statusCode(),response.body());
        var activity=activities.workspace(()->"creation-s0",course).activity();
        assertEquals(List.of(1,2),activity.groups().stream().map(GroupingActivities.Group::ordinal).toList());
        assertEquals(List.of("Group 1","Group 2"),activity.groups().stream().map(GroupingActivities.Group::name).toList());
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM activity_participant WHERE activity_id=?",Integer.class,activity.id()));
        Map<String,Object> projected=JsonPath.read(response.body(),"$.activity");
        assertEquals(Set.of("id","courseId","instructions","deadline","status","groups"),projected.keySet());
        var before=jdbc.queryForList("SELECT * FROM activity_group WHERE activity_id=? ORDER BY ordinal",activity.id());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",activity.id());
        assertEquals("FINALIZING",activities.workspace(()->"creation-s0",course).activity().status());
        assertEquals(before,jdbc.queryForList("SELECT * FROM activity_group WHERE activity_id=? ORDER BY ordinal",activity.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE activity_id=?",Integer.class,activity.id()));
    }
    @Test void rejectsInvalidInputAtomicallyAndAccepts5000UnicodeCodePoints() {
        var course=setup("validation",4);
        for(String text:List.of(""," \r\n\t","\u00a0\u0085\ufeff","x".repeat(5001),"😀".repeat(5001),"x\u0000x","\ud800","\udc00"))
            assertThrows(ActivityValidationException.class,()->create("validation",course,UUID.randomUUID(),new GroupingActivities.CreateActivity(text,valid().deadline(),List.of(2,2))));
        for(List<Integer> caps:List.of(List.<Integer>of(),List.of(1,3),List.of(2),List.of(3,3),List.of(Integer.MAX_VALUE,Integer.MAX_VALUE,6)))
            assertThrows(ActivityValidationException.class,()->create("validation",course,UUID.randomUUID(),new GroupingActivities.CreateActivity("ok",valid().deadline(),caps)));
        for(Instant deadline:List.of(Instant.EPOCH,Instant.now().minusSeconds(1)))
            assertThrows(ActivityValidationException.class,()->create("validation",course,UUID.randomUUID(),new GroupingActivities.CreateActivity("ok",deadline,List.of(2,2))));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_activity WHERE course_id=?",Integer.class,course));
        String text="😀".repeat(5000);
        assertEquals(text,create("validation",course,UUID.randomUUID(),new GroupingActivities.CreateActivity(text,valid().deadline(),List.of(2,2))).activity().instructions());
        assertEquals(5000,jdbc.queryForObject("SELECT char_length(instructions) FROM grouping_activity WHERE course_id=?",Integer.class,course));
        for(int count:List.of(0,1)) {
            var small=setup("small"+count,count);
            assertThrows(ActivityValidationException.class,()->create("small"+count,small,UUID.randomUUID(),new GroupingActivities.CreateActivity("ok",valid().deadline(),List.of(2))));
        }
    }
    @Test void strictHttpTypesAndSafeFailuresDoNotCoerceOrWrite() throws Exception {
        var course=setup("types",4); var c=login("types-teacher");
        String template=body(UUID.randomUUID(),valid());
        for(String caps:List.of("null","[]","[2.0,2]","[2.5,2]","[\"2\",2]","[true,2]","[{},2]","[2147483648,2]","[null,2]")) {
            String bad=template.replace("[2,2]",caps); var response=post(c,course,bad);
            assertEquals(400,response.statusCode(),bad+response.body()); assertFalse(response.body().contains("synthetic-password"));
        }
        for(String field:List.of("instructions","deadline","commandKey")) {
            var object=mapper.readTree(template); ((tools.jackson.databind.node.ObjectNode)object).put(field,42);
            assertEquals(400,post(c,course,mapper.writeValueAsString(object)).statusCode());
        }
        assertEquals(400,post(c,course,"{broken").statusCode());
        assertEquals(400,post(c,course,template.replace(valid().instructions(),"ignored").replace("\"instructions\":", "\"unused\":")).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_activity WHERE course_id=?",Integer.class,course));
    }
    @Test void realSessionAuthorizationCsrfAndPersistedAssignmentAreRechecked() throws Exception {
        var course=setup("access",4);String path="/api/courses/"+course+"/activity",body=body(UUID.randomUUID(),valid());
        assertEquals(401,get(client(),path).statusCode());
        assertEquals(403,post(login("access-s0"),course,body).statusCode());
        assertEquals(404,get(login("access-other"),path).statusCode());
        assertEquals(404,get(login("access-s4"),path).statusCode());
        var teacher=login("access-teacher");
        for(String token:List.of("","invalid"))assertEquals(403,request(teacher,path,"POST",body,token.isEmpty()?null:token,"application/json").statusCode());
        assertThrows(CourseNotAccessible.class,()->activities.create(()->"access-s0",course,UUID.randomUUID(),valid()));
        var key=UUID.randomUUID();var r=valid();create("access",course,key,r);
        jdbc.update("DELETE FROM course_teacher_assignment WHERE course_id=?",course);
        assertThrows(CourseNotAccessible.class,()->create("access",course,key,r));
        assertEquals(404,post(teacher,course,body).statusCode());
    }
    @Test void matchingReplayUsesCurrentMetadataButChangedInputNeverExecutes() {
        var course=setup("replay",4);var key=UUID.randomUUID();var r=valid();var first=create("replay",course,key,r);
        assertEquals(first,create("replay",course,key,r));
        jdbc.update("UPDATE grouping_activity SET instructions='Current metadata' WHERE id=?",first.activity().id());
        assertEquals("Current metadata",create("replay",course,key,r).activity().instructions());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(ActivityConflict.class,()->create("replay",course,key,new GroupingActivities.CreateActivity(r.instructions()+" ",r.deadline(),r.capacities()))).code());
        assertEquals("ACTIVITY_EXISTS",assertThrows(ActivityConflict.class,()->create("replay",course,UUID.randomUUID(),r)).code());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE activity_id=?",Integer.class,first.activity().id()));
    }
    @Test void lateEnrollmentsAreExcludedAndRemovedEnrollmentLosesReadAccess() {
        var course=setup("snapshot",4);var first=create("snapshot",course,UUID.randomUUID(),valid());
        var late=jdbc.queryForObject("SELECT id FROM app_account WHERE username='snapshot-s4'",UUID.class);
        jdbc.update("INSERT INTO course_enrollment(course_id,account_id) VALUES (?,?)",course,late);
        assertThrows(CourseNotAccessible.class,()->activities.workspace(()->"snapshot-s4",course));
        var removed=jdbc.queryForObject("SELECT id FROM app_account WHERE username='snapshot-s0'",UUID.class);
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",course,removed);
        assertThrows(CourseNotAccessible.class,()->activities.workspace(()->"snapshot-s0",course));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM activity_participant WHERE activity_id=?",Integer.class,first.activity().id()));
    }
    @Test void databaseConstraintsAndMidMutationFailureRollbackTheReceiptAndSnapshot() {
        var course=setup("rollback",4);var key=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_test_group() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.ordinal=2 THEN RAISE EXCEPTION 'synthetic failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_test_group BEFORE INSERT ON activity_group FOR EACH ROW EXECUTE FUNCTION reject_test_group()");
        try { assertThrows(org.springframework.dao.DataAccessException.class,()->create("rollback",course,key,valid())); }
        finally {jdbc.execute("DROP TRIGGER reject_test_group ON activity_group");jdbc.execute("DROP FUNCTION reject_test_group()");}
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_activity WHERE course_id=?",Integer.class,course));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
        var activity=create("rollback",course,key,valid()).activity();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,3,1)",UUID.randomUUID(),activity.id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,1,2)",UUID.randomUUID(),activity.id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO grouping_activity(id,course_id,instructions,deadline) VALUES (?,?,'duplicate',clock_timestamp())",UUID.randomUUID(),course));
    }
    @Test void v3UpgradePreservesAccountsRelationshipsProfilesAndSessions() {
        String schema="slice3_upgrade";jdbc.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("3").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl()+(POSTGRES.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,POSTGRES.getUsername(),POSTGRES.getPassword()));
        UUID student=UUID.randomUUID(),teacher=UUID.randomUUID(),course=UUID.randomUUID(),session=UUID.randomUUID();
        db.update("INSERT INTO app_account VALUES (?, 'preserved-student','hash','STUDENT'), (?, 'preserved-teacher','hash','TEACHER')",student,teacher);
        db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'00001','Preserved')",student);
        db.update("INSERT INTO course VALUES (?,'UPGRADE3','Preserved')",course);
        db.update("INSERT INTO course_teacher_assignment(course_id,account_id) VALUES (?,?)",course,teacher);
        db.update("INSERT INTO course_enrollment VALUES (?,?)",course,student);
        db.update("INSERT INTO course_introduction VALUES (?,?,'Original plain text')",course,student);
        db.update("INSERT INTO spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES (?,?,1,1,1800,1800001,'preserved-student')",session.toString(),UUID.randomUUID().toString());
        db.update("INSERT INTO spring_session_attributes VALUES (?,'preserved',?)",session.toString(),new byte[]{1,2,3});
        var tables=List.of("app_account","student_identity","course","course_teacher_assignment","course_enrollment","course_introduction","spring_session");
        var before=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,db.queryForList("SELECT * FROM "+table));
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table));
        assertArrayEquals(new byte[]{1,2,3},(byte[])db.queryForMap("SELECT * FROM spring_session_attributes").get("attribute_bytes"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM grouping_activity",Integer.class));
        assertEquals(9,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
    }
}
