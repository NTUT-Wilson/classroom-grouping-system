package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.ClassroomGroupingApplication;
import com.classroomgrouping.courses.api.*;
import com.classroomgrouping.grouping.api.*;
import com.classroomgrouping.identity.api.StudentIdentities;
import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
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
@SpringBootTest(classes=ClassroomGroupingApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.profiles.active=local","app.demo.enabled=false","app.roster.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class FinalResultsIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupingResults results;
    @Autowired GroupingActivities activities;
    @Autowired GroupingMemberships memberships;
    @Autowired GroupingApplications applications;
    @Autowired GroupingInvitations invitations;
    @Autowired CourseProfiles profiles;
    @Autowired ActivityCourseAccess access;
    @Autowired StudentIdentities identities;
    @Autowired CourseIntroductions introductions;
    @Autowired DueWork due;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    private record Fixture(UUID course,UUID activity,UUID teacher,List<UUID> students,List<UUID> groups) {}
    private Fixture fixture() {
        UUID course=UUID.randomUUID(),teacher=UUID.randomUUID();
        jdbc.update("INSERT INTO course VALUES (?,?,'Synthetic')",course,course.toString());
        account(teacher,"TEACHER");jdbc.update("INSERT INTO course_teacher_assignment(course_id,account_id) VALUES (?,?)",course,teacher);
        var students=new ArrayList<UUID>();
        for(int i=0;i<4;i++) {
            UUID id=UUID.randomUUID();students.add(id);account(id,"STUDENT");
            jdbc.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,?,?)",id,"000"+i+id,"Name 陳 😀 "+i);
            jdbc.update("INSERT INTO course_enrollment VALUES (?,?)",course,id);
            jdbc.update("INSERT INTO course_introduction VALUES (?,?,?)",course,id,"<plain> About 😀 "+i);
        }
        var a=activities.create(()->teacher.toString(),course,UUID.randomUUID(),new GroupingActivities.CreateActivity("Instructions",Instant.now().plusSeconds(3600),List.of(2,2))).activity();
        return new Fixture(course,a.id(),teacher,students,a.groups().stream().map(GroupingActivities.Group::id).toList());
    }
    private void account(UUID id,String role) {jdbc.update("INSERT INTO app_account VALUES (?,?,'{noop}synthetic-password',?)",id,id.toString(),role);}
    private void finish(Fixture f) {jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",f.activity());due.processDueWork();assertNotNull(jdbc.queryForObject("SELECT finalized_at FROM grouping_activity WHERE id=?",java.sql.Timestamp.class,f.activity()));}
    private GroupingResults.Result mine(Fixture f,int i){return results.myGroup(()->f.students().get(i).toString(),f.course());}
    private GroupingResults.Result roster(Fixture f){return results.roster(()->f.teacher().toString(),f.course());}
    private HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    private HttpResponse<String> request(HttpClient c,String path,String method,String body,String token) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)b.header("X-CSRF-TOKEN",token);
        return c.send(b.header("Content-Type","application/x-www-form-urlencoded").method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(HttpClient c,String path)throws Exception{return request(c,path,"GET","",null);}
    private HttpClient login(UUID account)throws Exception{
        var c=client();String token=JsonPath.read(get(c,"/api/auth/csrf").body(),"$.token");
        assertEquals(204,request(c,"/api/auth/login","POST","username="+account+"&password=synthetic-password",token).statusCode());return c;
    }
    private String base(Fixture f){return "/api/courses/"+f.course();}
    private Map<String,List<Map<String,Object>>> domain(Fixture f){
        var state=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("activity_participant","activity_group","activity_membership","allocation_plan","allocation_placement","allocation_work","application_attempt","invitation_attempt","grouping_command_receipt"))
            state.put(table,jdbc.queryForList("SELECT * FROM "+table+" WHERE activity_id=? ORDER BY 1,2",f.activity()));
        state.put("activity",jdbc.queryForList("SELECT * FROM grouping_activity WHERE id=?",f.activity()));return state;
    }
    @Test void noRosterBeforeDeadlineOrBeforeCompleteFinalizationAndReadsDoNotProcessWork(){
        var f=fixture();assertEquals("OPEN",roster(f).status());assertTrue(mine(f,0).groups().isEmpty());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",f.activity());
        var before=domain(f);assertEquals("FINALIZING",roster(f).status());assertTrue(roster(f).groups().isEmpty());assertTrue(mine(f,0).groups().isEmpty());assertEquals(before,domain(f));
        // A marker alone, even with all memberships absent, never authorizes partial output.
        jdbc.update("UPDATE grouping_activity SET finalized_at=clock_timestamp() WHERE id=?",f.activity());
        assertThrows(ResultsUnavailable.class,()->roster(f));assertThrows(ResultsUnavailable.class,()->mine(f,0));
    }
    @Test void finalizedTeacherAndOwnGroupProjectionsPreserveIdentityManagersAndReadOnlyState()throws Exception{
        var f=fixture();UUID key=UUID.randomUUID();memberships.joinEmpty(()->f.students().getFirst().toString(),f.course(),f.groups().getFirst(),key);
        finish(f);var before=domain(f);var roster=roster(f);assertEquals("FINALIZED",roster.status());assertEquals(List.of(1,2),roster.groups().stream().map(GroupingResults.Group::ordinal).toList());
        assertEquals(f.students().getFirst(),roster.groups().getFirst().managerAccountId());assertNull(roster.groups().getLast().managerAccountId());
        assertEquals(new HashSet<>(f.students()),roster.groups().stream().flatMap(g->g.members().stream()).map(GroupingResults.Member::accountId).collect(java.util.stream.Collectors.toSet()));
        for(int i=0;i<4;i++){UUID actor=f.students().get(i);var own=mine(f,i);assertEquals(1,own.groups().size());assertTrue(own.groups().getFirst().members().stream().anyMatch(m->m.accountId().equals(actor)));for(var member:own.groups().getFirst().members()){assertTrue(member.studentId().startsWith("000"));assertTrue(member.aboutMe().startsWith("<plain>"));}}
        var teacher=get(login(f.teacher()),base(f)+"/results/roster");assertEquals(200,teacher.statusCode());assertFalse(teacher.body().contains("aboutMe"));assertFalse(teacher.body().contains("placement"));
        var student=get(login(f.students().getFirst()),base(f)+"/results/my-group");assertEquals(200,student.statusCode());assertTrue(student.body().contains("aboutMe"));assertFalse(student.body().contains(f.groups().getLast().toString()));
        assertTrue(memberships.discover(()->f.students().getFirst().toString(),f.course()).groups().isEmpty());assertNull(memberships.joinEmpty(()->f.students().getFirst().toString(),f.course(),f.groups().getFirst(),key).ownGroupId());
        assertEquals("FINALIZED",activities.workspace(()->f.teacher().toString(),f.course()).activity().status());assertEquals(before,domain(f));
    }
    @Test void directHttpAndServiceEnforceRolesCourseFixedParticipantAndStaleAccess()throws Exception{
        var f=fixture();finish(f);var teacher=login(f.teacher());var student=login(f.students().getFirst());String own=base(f)+"/results/my-group",all=base(f)+"/results/roster";
        assertEquals(401,get(client(),own).statusCode());assertEquals(403,get(student,all).statusCode());assertEquals(403,get(teacher,own).statusCode());
        assertThrows(CourseNotAccessible.class,()->results.roster(()->f.students().getFirst().toString(),f.course()));assertThrows(CourseNotAccessible.class,()->results.myGroup(()->f.teacher().toString(),f.course()));
        var other=fixture();assertEquals(404,get(student,base(other)+"/results/my-group").statusCode());assertEquals(404,get(teacher,base(other)+"/results/roster").statusCode());assertEquals(404,get(student,"/api/courses/"+UUID.randomUUID()+"/results/my-group").statusCode());
        assertEquals(403,get(student,base(f)+"/results/groups/"+f.groups().getLast()).statusCode());
        UUID late=UUID.randomUUID();account(late,"STUDENT");jdbc.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,?,'Late')",late,late.toString());jdbc.update("INSERT INTO course_enrollment VALUES (?,?)",f.course(),late);assertEquals(404,get(login(late),own).statusCode());
        jdbc.update("DELETE FROM course_introduction WHERE course_id=? AND account_id=?",f.course(),f.students().getFirst());jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",f.course(),f.students().getFirst());assertEquals(404,get(student,own).statusCode());
        jdbc.update("DELETE FROM course_teacher_assignment WHERE course_id=? AND account_id=?",f.course(),f.teacher());assertEquals(404,get(teacher,all).statusCode());
        jdbc.update("UPDATE spring_session SET last_access_time=0,expiry_time=0");assertEquals(401,get(student,own).statusCode());
    }
    @Test void currentPersistedRoleOverridesStaleSessionAuthority()throws Exception{
        var f=fixture();finish(f);var teacher=login(f.teacher());jdbc.update("DELETE FROM course_teacher_assignment WHERE account_id=?",f.teacher());jdbc.update("UPDATE app_account SET role='STUDENT' WHERE id=?",f.teacher());
        assertEquals(404,get(teacher,base(f)+"/results/roster").statusCode());assertThrows(CourseNotAccessible.class,()->roster(f));
    }
    @Test void missingOwnOrOtherMembershipAndBadTerminalStateFailWholeSnapshotSafely()throws Exception{
        for(boolean otherGroup:List.of(false,true)){
            var f=fixture();finish(f);UUID ownGroup=mine(f,0).groups().getFirst().id();UUID missing=otherGroup?roster(f).groups().stream().filter(g->!g.id().equals(ownGroup)).findFirst().orElseThrow().members().getFirst().accountId():f.students().getFirst();jdbc.update("DELETE FROM activity_membership WHERE activity_id=? AND account_id=?",f.activity(),missing);
            var before=domain(f);assertThrows(ResultsUnavailable.class,()->mine(f,0));assertThrows(ResultsUnavailable.class,()->roster(f));
            var response=get(login(f.students().getFirst()),base(f)+"/results/my-group");assertEquals(503,response.statusCode());assertEquals("RESULTS_UNAVAILABLE",JsonPath.read(response.body(),"$.code"));assertFalse(response.body().contains("groupId"));assertFalse(response.body().contains("SQL"));assertEquals(before,domain(f));
        }
        var f=fixture();finish(f);jdbc.update("UPDATE allocation_work SET state='PREPARED' WHERE activity_id=?",f.activity());assertThrows(ResultsUnavailable.class,()->roster(f));
    }
    @Test void finalProfilesAndInstructionsStayEditableWithoutAssignmentLeakOrRosterChanges(){
        var f=fixture();finish(f);var before=domain(f);String text=" <b>Plain 陳 😀</b>\r\n";
        profiles.save(()->f.students().getFirst().toString(),f.course(),text);assertEquals(text,mine(f,0).groups().getFirst().members().stream().filter(m->m.accountId().equals(f.students().getFirst())).findFirst().orElseThrow().aboutMe());
        activities.updateInstructions(()->f.teacher().toString(),f.course(),UUID.randomUUID(),"Changed after final");
        var after=domain(f);var activityBefore=new HashMap<>(before.remove("activity").getFirst());var activityAfter=new HashMap<>(after.remove("activity").getFirst());activityBefore.remove("instructions");activityAfter.remove("instructions");assertEquals(activityBefore,activityAfter);var oldReceipts=before.remove("grouping_command_receipt");var newReceipts=after.remove("grouping_command_receipt");assertTrue(newReceipts.containsAll(oldReceipts));assertEquals(oldReceipts.size()+1,newReceipts.size());assertEquals(1,newReceipts.stream().filter(r->"UPDATE_INSTRUCTIONS".equals(r.get("command"))).count());assertEquals(before,after);
        for(var p:profiles.classmates(()->f.students().getFirst().toString(),f.course()))assertNotNull(p.studentId());
        assertEquals("FINALIZED",activities.workspace(()->f.teacher().toString(),f.course()).activity().status());
        assertThrows(ActivityConflict.class,()->activities.extendDeadline(()->f.teacher().toString(),f.course(),UUID.randomUUID(),Instant.now().plusSeconds(7200)));
    }
    @Test void finalizedHistoryPaginationAndMutationReplayRemainPrivateAndNoAssignmentsInProfiles()throws Exception{
        var f=fixture();UUID manager=f.students().getFirst(),applicant=f.students().get(1),recipient=f.students().get(2),group=f.groups().getFirst();
        UUID joinKey=UUID.randomUUID();memberships.joinEmpty(()->manager.toString(),f.course(),group,joinKey);
        for(int i=0;i<3;i++){
            var app=applications.apply(()->applicant.toString(),f.course(),group,UUID.randomUUID()).items().getFirst();applications.decide(()->manager.toString(),f.course(),app.id(),UUID.randomUUID(),"REJECT");
            var inv=invitations.invite(()->manager.toString(),f.course(),group,recipient,UUID.randomUUID()).items().getFirst();invitations.decide(()->recipient.toString(),f.course(),inv.id(),UUID.randomUUID(),"REJECT");
        }
        UUID appKey=UUID.randomUUID(),invKey=UUID.randomUUID();applications.apply(()->applicant.toString(),f.course(),group,appKey);invitations.invite(()->manager.toString(),f.course(),group,recipient,invKey);finish(f);var before=domain(f);
        String cursor=null;int seen=0;do{var page=applications.history(()->applicant.toString(),f.course(),cursor,2);assertEquals("FINALIZED",page.status());for(var a:page.items()){assertEquals(applicant,a.applicant().accountId());assertNotEquals("PENDING",a.status());seen++;}cursor=page.nextCursor();}while(cursor!=null);assertEquals(4,seen);
        cursor=null;seen=0;do{var page=invitations.history(()->recipient.toString(),f.course(),cursor,2);assertEquals("FINALIZED",page.status());for(var a:page.items()){assertEquals(recipient,a.recipient().accountId());assertNotEquals("PENDING",a.status());seen++;}cursor=page.nextCursor();}while(cursor!=null);assertEquals(4,seen);
        assertTrue(applications.history(()->recipient.toString(),f.course(),null,2).items().isEmpty());assertTrue(invitations.history(()->applicant.toString(),f.course(),null,2).items().isEmpty());
        assertEquals("FINALIZED",applications.apply(()->applicant.toString(),f.course(),group,appKey).status());assertEquals("FINALIZED",invitations.invite(()->manager.toString(),f.course(),group,recipient,invKey).status());assertTrue(memberships.joinEmpty(()->manager.toString(),f.course(),group,joinKey).groups().isEmpty());
        var c=login(applicant);for(String path:List.of("/classmates","/applications","/invitations","/grouping")){var response=get(c,base(f)+path);assertEquals(200,response.statusCode());assertFalse(response.body().contains("ownGroupId\":\""));assertFalse(response.body().contains("managerAccountId"));assertFalse(response.body().contains("members"));}
        assertEquals(before,domain(f));
    }
    @Test void authorizedAbsentCourseReturnsOnlyAbsentStatus(){
        var f=fixture();UUID empty=UUID.randomUUID();jdbc.update("INSERT INTO course VALUES (?,?,'Empty')",empty,empty.toString());jdbc.update("INSERT INTO course_teacher_assignment(course_id,account_id) VALUES (?,?)",empty,f.teacher());jdbc.update("INSERT INTO course_enrollment VALUES (?,?)",empty,f.students().getFirst());
        assertEquals(new GroupingResults.Result(null,"ABSENT",List.of()),results.roster(()->f.teacher().toString(),empty));assertEquals(new GroupingResults.Result(null,"ABSENT",List.of()),results.myGroup(()->f.students().getFirst().toString(),empty));
    }
    @Test void identityAndIntroductionReadsShareOneRepeatableReadSnapshot(){
        var f=fixture();finish(f);UUID id=f.students().getFirst();String oldName=identities.lookup(List.of(id)).get(id).studentName();
        StudentIdentities concurrent=ids->{
            // Independent connection commits between public API lookups, after the result transaction's snapshot began.
            var separate=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
            separate.update("UPDATE student_identity SET student_name='New identity' WHERE account_id=?",id);separate.update("UPDATE course_introduction SET about_me='New introduction' WHERE course_id=? AND account_id=?",f.course(),id);
            return identities.lookup(ids);
        };
        var service=new DatabaseGroupingResults(jdbc,access,concurrent,introductions);var tx=new TransactionTemplate(transactions);tx.setReadOnly(true);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var snapshot=tx.execute(s->service.myGroup(()->id.toString(),f.course()));var member=snapshot.groups().getFirst().members().stream().filter(m->m.accountId().equals(id)).findFirst().orElseThrow();
        assertEquals(oldName,member.studentName());assertEquals("<plain> About 😀 0",member.aboutMe());
        var current=mine(f,0).groups().getFirst().members().stream().filter(m->m.accountId().equals(id)).findFirst().orElseThrow();assertEquals("New identity",current.studentName());assertEquals("New introduction",current.aboutMe());
    }
}
