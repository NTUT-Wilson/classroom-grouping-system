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
class GroupJoiningIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RosterProvisioning provisioning;
    @Autowired CourseWorkspace courses;
    @Autowired GroupingActivities activities;
    @Autowired GroupingMemberships memberships;
    @Autowired com.classroomgrouping.courses.api.CourseProfiles profiles;
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
    private GroupingActivities.Activity fixture(String prefix) {
        return create(prefix,setup(prefix,4),UUID.randomUUID(),valid()).activity();
    }
    private HttpResponse<String> settings(HttpClient c, UUID course, String suffix, String method, Map<String,Object> body) throws Exception {
        return request(c,"/api/courses/"+course+"/activity/"+suffix,method,mapper.writeValueAsString(body),csrf(c),"application/json");
    }
    private UUID account(String username) { return jdbc.queryForObject("SELECT id FROM app_account WHERE username=?",UUID.class,username); }
    private GroupingMemberships.Discovery join(String prefix, GroupingActivities.Activity a, int student, int group, UUID key) {
        return memberships.joinEmpty(()->prefix+"-s"+student,a.courseId(),a.groups().get(group).id(),key);
    }
    @Test void discoveryFirstJoinAndCurrentUnicodeProfiles() {
        var course=setup("join-profile",4);
        assertEquals(new GroupingMemberships.Discovery(null,"ABSENT",null,List.of()),memberships.discover(()->"join-profile-s0",course));
        var a=create("join-profile",course,UUID.randomUUID(),valid()).activity();
        var empty=memberships.discover(()->"join-profile-s0",course);
        assertEquals("OPEN",empty.status());assertNull(empty.ownGroupId());assertEquals(2,empty.groups().size());
        assertTrue(empty.groups().stream().allMatch(g->g.members().isEmpty()&&g.manager()==null&&g.availablePlaces()==2));
        jdbc.update("UPDATE student_identity SET student_id='000陳😀' WHERE account_id=?",account("join-profile-s0"));
        profiles.save(()->"join-profile-s0",course,"  <script>陳 😀\r\n");
        var joined=join("join-profile",a,0,0,UUID.randomUUID());var group=joined.groups().getFirst();
        assertEquals(group.id(),joined.ownGroupId());assertEquals(1,group.availablePlaces());assertEquals(List.of(group.manager()),group.members());
        assertEquals("000陳😀",group.manager().studentId());assertEquals("  <script>陳 😀\r\n",group.manager().aboutMe());
        profiles.save(()->"join-profile-s0",course,"Updated 😀");
        assertEquals("Updated 😀",memberships.discover(()->"join-profile-s1",course).groups().getFirst().members().getFirst().aboutMe());
        assertEquals("GROUP_NOT_EMPTY",assertThrows(GroupingConflict.class,()->join("join-profile",a,1,0,UUID.randomUUID())).code());
        assertEquals("This group is no longer empty",assertThrows(GroupingConflict.class,()->join("join-profile",a,1,0,UUID.randomUUID())).safeMessage());
        assertEquals("ALREADY_GROUPED",assertThrows(GroupingConflict.class,()->join("join-profile",a,0,1,UUID.randomUUID())).code());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?",Integer.class,a.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command='JOIN_EMPTY_GROUP' AND activity_id=?",Integer.class,a.id()));
    }
    @Test void replayIsCurrentPrivateAndReauthorized() {
        var a=fixture("join-replay");var key=UUID.randomUUID();
        var first=join("join-replay",a,0,0,key);
        assertEquals(first,join("join-replay",a,0,0,key));
        assertEquals("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,()->join("join-replay",a,0,1,key)).code());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",a.id());
        var closed=join("join-replay",a,0,0,key);
        assertEquals("FINALIZING",closed.status());assertNull(closed.ownGroupId());assertTrue(closed.groups().isEmpty());
        assertEquals("ACTIVITY_CLOSED",assertThrows(GroupingConflict.class,()->join("join-replay",a,0,0,UUID.randomUUID())).code());
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",a.courseId(),account("join-replay-s0"));
        assertThrows(CourseNotAccessible.class,()->join("join-replay",a,0,0,key));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM activity_participant WHERE activity_id=?",Integer.class,a.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?",Integer.class,a.id()));
    }
    @Test void persistedRoleEnrollmentAndFixedParticipantAreRequired() {
        var a=fixture("join-access");
        for(String user:List.of("join-access-teacher","join-access-other","join-access-s4")) {
            assertThrows(CourseNotAccessible.class,()->memberships.discover(()->user,a.courseId()));
            assertThrows(CourseNotAccessible.class,()->memberships.joinEmpty(()->user,a.courseId(),a.groups().getFirst().id(),UUID.randomUUID()));
        }
        jdbc.update("INSERT INTO course_enrollment VALUES (?,?)",a.courseId(),account("join-access-s4"));
        assertThrows(CourseNotAccessible.class,()->memberships.discover(()->"join-access-s4",a.courseId()));
        assertThrows(CourseNotAccessible.class,()->join("join-access",a,4,0,UUID.randomUUID()));
        var staleStudentRole=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "join-access-teacher","unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT")));
        assertThrows(CourseNotAccessible.class,()->memberships.discover(staleStudentRole,a.courseId()));
        assertThrows(CourseNotAccessible.class,()->memberships.joinEmpty(staleStudentRole,a.courseId(),a.groups().getFirst().id(),UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class,()->memberships.joinEmpty(()->"join-access-s1",a.courseId(),UUID.randomUUID(),UUID.randomUUID()));
        assertThrows(GroupingValidationException.class,()->join("join-access",a,1,0,null));
    }
    @Test void fixedMemberIdentitySurvivesMutableEnrollmentAndClosedReadsNeverWrite() {
        var a=fixture("join-fixed");join("join-fixed",a,0,0,UUID.randomUUID());
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",a.courseId(),account("join-fixed-s0"));
        assertEquals(account("join-fixed-s0"),memberships.discover(()->"join-fixed-s1",a.courseId()).groups().getFirst().members().getFirst().accountId());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());
        var tables=List.of("grouping_activity","activity_group","activity_participant","activity_membership","grouping_command_receipt");
        var before=new HashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,jdbc.queryForList("SELECT * FROM "+table));
        for(int i=0;i<3;i++) {var closed=memberships.discover(()->"join-fixed-s1",a.courseId());assertEquals("FINALIZING",closed.status());assertNull(closed.ownGroupId());assertTrue(closed.groups().isEmpty());}
        assertEquals("ACTIVITY_CLOSED",assertThrows(GroupingConflict.class,()->join("join-fixed",a,1,0,UUID.randomUUID())).code());
        for(String table:tables)assertEquals(before.get(table),jdbc.queryForList("SELECT * FROM "+table));
    }
    @Test void membershipAndManagerMustReferenceSameActivityAndGroup() {
        var a=fixture("join-db");var b=fixture("join-db-other");var student=account("join-db-s0");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO activity_membership VALUES (?,?,?)",a.id(),student,b.groups().getFirst().id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO activity_membership VALUES (?,?,?)",a.id(),account("join-db-s4"),a.groups().getFirst().id()));
        join("join-db",a,0,0,UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO activity_membership VALUES (?,?,?)",a.id(),student,a.groups().get(1).id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",student,a.groups().get(1).id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM activity_membership WHERE activity_id=? AND account_id=?",a.id(),student));
    }
    @Test void managerWriteFailureRollsBackMembershipAndReceipt() {
        var a=fixture("join-rollback");var key=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_join_manager() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_join_manager BEFORE UPDATE ON activity_group FOR EACH ROW EXECUTE FUNCTION reject_join_manager()");
        try {assertThrows(org.springframework.dao.DataAccessException.class,()->join("join-rollback",a,0,0,key));}
        finally {jdbc.execute("DROP TRIGGER reject_join_manager ON activity_group");jdbc.execute("DROP FUNCTION reject_join_manager()");}
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?",Integer.class,a.id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
        assertTrue(memberships.discover(()->"join-rollback-s0",a.courseId()).groups().stream().allMatch(g->g.manager()==null));
        assertNotNull(join("join-rollback",a,0,0,key).ownGroupId());
    }
    @Test void httpDeniesTeachersCsrfInjectionAndInvalidKeysWithoutLeakingProfiles() throws Exception {
        var a=fixture("join-http");String path="/api/courses/"+a.courseId()+"/groups/"+a.groups().getFirst().id()+"/join";
        var c=login("join-http-s0");String body=mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID()));
        assertEquals(401,get(client(),"/api/courses/"+a.courseId()+"/grouping").statusCode());
        assertEquals(403,request(c,path,"POST",body,null,"application/json").statusCode());
        for(String user:List.of("join-http-teacher","join-http-other","join-http-s4")) {
            var denied=login(user);var read=get(denied,"/api/courses/"+a.courseId()+"/grouping");
            assertEquals(user.contains("teacher")||user.endsWith("other")?403:404,read.statusCode(),read.body());
            assertFalse(read.body().contains("Synthetic 0"));
            var write=request(denied,path,"POST",body,csrf(denied),"application/json");
            assertEquals(user.contains("teacher")||user.endsWith("other")?403:404,write.statusCode(),write.body());
        }
        for(String invalid:List.of("{}","null","[]","{broken","{\"commandKey\":42}","{\"commandKey\":true}","{\"commandKey\":\"1-1-1-1-1\"}","{\"commandKey\":\""+UUID.randomUUID()+"\",\"manager\":\"injected\"}")) {
            var response=request(c,path,"POST",invalid,csrf(c),"application/json");assertEquals(400,response.statusCode(),response.body());assertFalse(response.body().contains("injected"));
        }
        assertEquals(200,request(c,path,"POST",body,csrf(c),"application/json").statusCode());
        var loser=login("join-http-s1");var conflict=request(loser,path,"POST",mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID())),csrf(loser),"application/json");
        assertEquals(409,conflict.statusCode());assertEquals("This group is no longer empty",JsonPath.read(conflict.body(),"$.detail"));
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());
        var closed=get(c,"/api/courses/"+a.courseId()+"/grouping");assertEquals(200,closed.statusCode());
        assertFalse(closed.body().contains("Synthetic 0"));assertFalse(closed.body().contains("000join-http-s0"));
        assertEquals("FINALIZING",JsonPath.read(closed.body(),"$.status"));
    }
    @Test void v4UpgradePreservesActivityParticipantsLayoutAndReceipts() {
        String schema="slice5_upgrade";jdbc.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("4").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl()+(POSTGRES.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,POSTGRES.getUsername(),POSTGRES.getPassword()));
        UUID student=UUID.randomUUID(),course=UUID.randomUUID(),activity=UUID.randomUUID(),group=UUID.randomUUID();
        db.update("INSERT INTO app_account VALUES (?,'preserved-join-student','hash','STUDENT')",student);
        db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'000陳😀','Preserved')",student);
        db.update("INSERT INTO course VALUES (?,'UPGRADE5','Preserved')",course);db.update("INSERT INTO course_enrollment VALUES (?,?)",course,student);
        db.update("INSERT INTO course_introduction VALUES (?,?,'Preserved 😀')",course,student);
        db.update("INSERT INTO grouping_activity VALUES (?,?,'Instructions',clock_timestamp()+interval '1 hour')",activity,course);
        db.update("INSERT INTO activity_participant VALUES (?,?)",activity,student);db.update("INSERT INTO activity_group VALUES (?,?,1,2)",group,activity);
        db.update("INSERT INTO grouping_command_receipt VALUES (?,'CREATE_ACTIVITY',?,'preserved-fingerprint',?)",student,UUID.randomUUID(),activity);
        var tables=List.of("app_account","student_identity","course","course_enrollment","course_introduction","grouping_activity","activity_participant","grouping_command_receipt");
        var before=new HashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,db.queryForList("SELECT * FROM "+table));
        var layout=db.queryForList("SELECT id,activity_id,ordinal,capacity FROM activity_group");
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table));
        assertEquals(layout,db.queryForList("SELECT id,activity_id,ordinal,capacity FROM activity_group"));
        assertNull(db.queryForMap("SELECT * FROM activity_group").get("manager_account_id"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM activity_membership",Integer.class));
        assertEquals(5,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
    }
}

