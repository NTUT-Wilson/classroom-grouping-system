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
class ApplicationIntegrationTest {
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
    @Autowired GroupingApplications applications;
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

    private GroupingApplications.ApplicationPage apply(String prefix,GroupingActivities.Activity a,int student,int group,UUID key){return applications.apply(()->prefix+"-s"+student,a.courseId(),a.groups().get(group).id(),key);}
    private GroupingApplications.ApplicationPage history(String prefix,GroupingActivities.Activity a,int student){return applications.history(()->prefix+"-s"+student,a.courseId(),null,null);}
    private GroupingApplications.ApplicationPage decide(String prefix,GroupingActivities.Activity a,int manager,UUID attempt,String decision,UUID key){return applications.decide(()->prefix+"-s"+manager,a.courseId(),attempt,key,decision);}
    @Test void pendingDoesNotReserveAndRejectionAllowsRetainedNewAttemptsAndSafeReplay(){
        String p="app-history";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());
        UUID key=UUID.randomUUID();var first=apply(p,a,1,0,key).items().getFirst();
        assertEquals("PENDING",first.status());assertEquals(1,memberships.discover(()->p+"-s1",a.courseId()).groups().getFirst().availablePlaces());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?",Integer.class,a.id()));
        assertEquals(first.id(),apply(p,a,1,0,key).items().getFirst().id());
        assertEquals("APPLICATION_PENDING",assertThrows(GroupingConflict.class,()->apply(p,a,1,0,UUID.randomUUID())).code());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,()->apply(p,a,1,1,key)).code());
        UUID decisionKey=UUID.randomUUID();assertEquals("REJECTED",decide(p,a,0,first.id(),"REJECT",decisionKey).items().getFirst().status());
        var second=apply(p,a,1,0,UUID.randomUUID()).items().getFirst();assertNotEquals(first.id(),second.id());
        assertEquals(2,apply(p,a,1,0,key).items().size());
        assertEquals(2,decide(p,a,0,first.id(),"REJECT",decisionKey).items().size());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,()->decide(p,a,0,first.id(),"APPROVE",decisionKey)).code());
        assertEquals("APPLICATION_NOT_PENDING",assertThrows(GroupingConflict.class,()->decide(p,a,0,first.id(),"REJECT",UUID.randomUUID())).code());
        assertEquals(List.of("PENDING","REJECTED"),history(p,a,1).items().stream().map(GroupingApplications.ApplicationAttempt::status).toList());
        assertTrue(history(p,a,2).items().isEmpty());assertThrows(CourseNotAccessible.class,()->decide(p,a,1,second.id(),"APPROVE",UUID.randomUUID()));
    }
    @Test void approvalAndEmptyJoiningUseAtomicCleanupAndExactReasons(){
        String p="app-cleanup";UUID course=setup(p,6);var a=create(p,course,UUID.randomUUID(),new GroupingActivities.CreateActivity("Synthetic",Instant.now().plusSeconds(3600),List.of(2,2,2))).activity();
        join(p,a,0,0,UUID.randomUUID());join(p,a,1,1,UUID.randomUUID());
        var used=apply(p,a,2,0,UUID.randomUUID()).items().getFirst();var other=apply(p,a,2,1,UUID.randomUUID()).items().getFirst();
        var leftover=apply(p,a,3,0,UUID.randomUUID()).items().getFirst();var untouched=apply(p,a,3,1,UUID.randomUUID()).items().getFirst();
        var accepted=decide(p,a,0,used.id(),"APPROVE",UUID.randomUUID());assertTrue(accepted.items().stream().anyMatch(x->x.id().equals(used.id())&&x.status().equals("ACCEPTED")));
        assertEquals("Joined a group",jdbc.queryForObject("SELECT reason FROM application_attempt WHERE id=?",String.class,other.id()));
        assertEquals("Group full",jdbc.queryForObject("SELECT reason FROM application_attempt WHERE id=?",String.class,leftover.id()));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM application_attempt WHERE id=?",String.class,untouched.id()));
        join(p,a,3,2,UUID.randomUUID());assertEquals("Joined a group",jdbc.queryForObject("SELECT reason FROM application_attempt WHERE id=?",String.class,untouched.id()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?",Integer.class,a.id(),used.groupId()));
        assertEquals("ALREADY_GROUPED",assertThrows(GroupingConflict.class,()->apply(p,a,2,1,UUID.randomUUID())).code());
        assertEquals("GROUP_FULL",assertThrows(GroupingConflict.class,()->apply(p,a,4,0,UUID.randomUUID())).code());
    }
    @Test void everyPageIsPrivateAndReauthorizedAfterCutoffWithNoReadWrites(){
        String p="app-pages";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());
        var expected=new HashSet<UUID>();
        for(int i=0;i<24;i++){var x=apply(p,a,1,0,UUID.randomUUID()).items().getFirst();expected.add(x.id());decide(p,a,0,x.id(),"REJECT",UUID.randomUUID());}
        expected.add(apply(p,a,1,0,UUID.randomUUID()).items().getFirst().id());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());
        var before=jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id",a.id());
        String cursor=null;var seen=new HashSet<UUID>();String next=null;
        do{var page=applications.history(()->p+"-s1",a.courseId(),cursor,3);assertEquals("FINALIZING",page.status());assertTrue(page.items().size()<=3);for(var x:page.items()){assertTrue(seen.add(x.id()));assertTrue(Set.of("EXPIRED","REJECTED").contains(x.status()));}cursor=page.nextCursor();if(cursor!=null)next=cursor;}while(cursor!=null);
        assertEquals(expected,seen);assertEquals(before,jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id",a.id()));
        String bound=next;assertThrows(GroupingValidationException.class,()->applications.history(()->p+"-s2",a.courseId(),bound,3));
        for(Integer limit:List.of(0,51))assertThrows(GroupingValidationException.class,()->applications.history(()->p+"-s1",a.courseId(),null,limit));
        assertThrows(GroupingValidationException.class,()->applications.history(()->p+"-s1",a.courseId(),"bad",3));
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",a.courseId(),account(p+"-s1"));
        assertThrows(CourseNotAccessible.class,()->applications.history(()->p+"-s1",a.courseId(),bound,3));
        assertTrue(history(p,a,2).items().isEmpty());
        assertThrows(CourseNotAccessible.class,()->applications.history(()->p+"-teacher",a.courseId(),null,null));
    }
    @Test void cutoffBlocksAllNewMutationsButReplayReauthorizesCurrentProjection(){
        String p="app-cutoff";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());UUID key=UUID.randomUUID();var x=apply(p,a,1,0,key).items().getFirst();
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());
        assertEquals("EXPIRED",apply(p,a,1,0,key).items().getFirst().status());
        assertEquals("ACTIVITY_CLOSED",assertThrows(GroupingConflict.class,()->apply(p,a,2,0,UUID.randomUUID())).code());
        for(String decision:List.of("APPROVE","REJECT"))assertEquals("ACTIVITY_CLOSED",assertThrows(GroupingConflict.class,()->decide(p,a,0,x.id(),decision,UUID.randomUUID())).code());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM application_attempt WHERE id=?",String.class,x.id()));
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",a.courseId(),account(p+"-s1"));assertThrows(CourseNotAccessible.class,()->apply(p,a,1,0,key));
    }
    @Test void approvalFailureRollsBackMembershipOutcomeCleanupAndReceipt(){
        String p="app-rollback";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());var x=apply(p,a,1,0,UUID.randomUUID()).items().getFirst();apply(p,a,2,0,UUID.randomUUID());UUID key=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_application_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_application_cleanup BEFORE UPDATE ON application_attempt FOR EACH ROW WHEN (NEW.status='CANCELED') EXECUTE FUNCTION reject_application_cleanup()");
        try{assertThrows(org.springframework.dao.DataAccessException.class,()->decide(p,a,0,x.id(),"APPROVE",key));}
        finally{jdbc.execute("DROP TRIGGER reject_application_cleanup ON application_attempt");jdbc.execute("DROP FUNCTION reject_application_cleanup()");}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?",Integer.class,a.id()));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM application_attempt WHERE activity_id=? AND status='PENDING'",Integer.class,a.id()));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
        assertTrue(decide(p,a,0,x.id(),"APPROVE",key).items().stream().anyMatch(i->i.status().equals("ACCEPTED")));
    }
    @Test void httpSessionCsrfStrictDtosAndPrivateHistory()throws Exception{
        String p="app-http";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());String base="/api/courses/"+a.courseId();String path=base+"/groups/"+a.groups().getFirst().id()+"/applications";var c=login(p+"-s1");String body=mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID()));
        assertEquals(401,get(client(),base+"/applications").statusCode());assertEquals(403,request(c,path,"POST",body,null,"application/json").statusCode());
        for(String invalid:List.of("{}","null","[]","{broken","{\"commandKey\":true}","{\"commandKey\":\"1-1-1-1-1\"}","{\"commandKey\":\""+UUID.randomUUID()+"\",\"status\":\"ACCEPTED\"}"))assertEquals(400,request(c,path,"POST",invalid,csrf(c),"application/json").statusCode());
        var response=request(c,path,"POST",body,csrf(c),"application/json");assertEquals(200,response.statusCode(),response.body());assertFalse(response.body().contains("ownGroupId"));assertFalse(response.body().contains("members"));
        String extreme=Base64.getUrlEncoder().withoutPadding().encodeToString((account(p+"-s1")+"|"+a.courseId()+"|"+Instant.MAX+"|"+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(String query:List.of("?cursor=bad","?cursor="+extreme,"?limit=0","?limit=51","?limit=oops"))assertEquals(400,get(c,base+"/applications"+query).statusCode());
        UUID attempt=UUID.fromString(JsonPath.read(response.body(),"$.items[0].id"));String decision=base+"/applications/"+attempt+"/decision";var manager=login(p+"-s0");
        for(String bad:List.of("{\"commandKey\":\""+UUID.randomUUID()+"\",\"decision\":true}","{\"commandKey\":\""+UUID.randomUUID()+"\",\"decision\":\"ACCEPT\"}","{\"commandKey\":\""+UUID.randomUUID()+"\",\"decision\":\"REJECT\",\"reason\":\"injected\"}"))assertEquals(400,request(manager,decision,"POST",bad,csrf(manager),"application/json").statusCode());
        assertEquals(403,get(login(p+"-teacher"),base+"/applications").statusCode());assertEquals(404,get(login(p+"-s4"),base+"/applications").statusCode());
        var peer=get(login(p+"-s2"),base+"/applications");assertEquals(200,peer.statusCode());assertFalse(peer.body().contains(attempt.toString()));
        String approve=mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID(),"decision","APPROVE"));assertEquals(403,request(manager,decision,"POST",approve,null,"application/json").statusCode());assertEquals(404,request(c,decision,"POST",approve,csrf(c),"application/json").statusCode());assertEquals(200,request(manager,decision,"POST",approve,csrf(manager),"application/json").statusCode());
    }
    @Test void databaseProtectsSameActivityPartiesPendingPairAndTerminalReasons(){
        String p="app-constraints";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());var x=apply(p,a,1,0,UUID.randomUUID()).items().getFirst();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),a.id(),x.groupId(),account(p+"-s1"),account(p+"-s0")));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE application_attempt SET status='CANCELED',reason=NULL WHERE id=?",x.id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE application_attempt SET reason='Group full' WHERE id=?",x.id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE application_attempt SET manager_account_id=? WHERE id=?",account(p+"-s2"),x.id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE application_attempt SET applicant_account_id=? WHERE id=?",account(p+"-s4"),x.id()));
        var foreign=fixture("app-foreign");join("app-foreign",foreign,0,0,UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE application_attempt SET group_id=? WHERE id=?",foreign.groups().getFirst().id(),x.id()));
        decide(p,a,0,x.id(),"REJECT",UUID.randomUUID());assertEquals(2,apply(p,a,1,0,UUID.randomUUID()).items().size());
    }
    @Test void v5UpgradePreservesMembershipManagerProfilesHistoryAndReceipts(){
        String schema="slice6_upgrade";jdbc.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("5").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl()+(POSTGRES.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,POSTGRES.getUsername(),POSTGRES.getPassword()));
        UUID student=UUID.randomUUID(),course=UUID.randomUUID(),activity=UUID.randomUUID(),group=UUID.randomUUID();
        db.update("INSERT INTO app_account VALUES (?,'preserved-app-student','hash','STUDENT')",student);
        db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'000陳😀','Preserved')",student);
        db.update("INSERT INTO course VALUES (?,'UPGRADE6','Preserved')",course);db.update("INSERT INTO course_enrollment VALUES (?,?)",course,student);
        db.update("INSERT INTO course_introduction VALUES (?,?,'Preserved 😀')",course,student);
        db.update("INSERT INTO grouping_activity VALUES (?,?,'Instructions',clock_timestamp()+interval '1 hour')",activity,course);
        db.update("INSERT INTO activity_participant VALUES (?,?)",activity,student);db.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,1,2)",group,activity);
        db.update("INSERT INTO activity_membership VALUES (?,?,?)",activity,student,group);db.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",student,group);
        db.update("INSERT INTO grouping_command_receipt VALUES (?,'JOIN_EMPTY_GROUP',?,'preserved-fingerprint',?)",student,UUID.randomUUID(),activity);
        var tables=List.of("app_account","student_identity","course","course_enrollment","course_introduction","grouping_activity","activity_participant","activity_group","activity_membership","grouping_command_receipt");
        var before=new HashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,db.queryForList("SELECT * FROM "+table));
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM application_attempt",Integer.class));assertEquals(6,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
    }
}
