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
class InvitationIntegrationTest {
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
    @Autowired GroupingInvitations invitations;
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

    private GroupingInvitations.InvitationAttempt send(String p,GroupingActivities.Activity a,int manager,int recipient,int group,UUID key){
        var page=invitations.invite(()->p+"-s"+manager,a.courseId(),a.groups().get(group).id(),account(p+"-s"+recipient),key);
        return page.items().stream().filter(x->x.recipient().accountId().equals(account(p+"-s"+recipient))).findFirst().orElseThrow();
    }
    private GroupingInvitations.InvitationPage respond(String p,GroupingActivities.Activity a,int recipient,UUID id,String decision,UUID key){return invitations.decide(()->p+"-s"+recipient,a.courseId(),id,key,decision);}
    private String state(String table,UUID id,String field){return jdbc.queryForObject("SELECT "+field+" FROM "+table+" WHERE id=?",String.class,id);}
    @Test void currentAuthorityControlsInheritedHistoryDecisionsCursorsAndReplaysAfterCutoff(){
        String p="v2-authority";UUID course=setup(p,6);
        var a=create(p,course,UUID.randomUUID(),new GroupingActivities.CreateActivity("Synthetic",Instant.now().plusSeconds(3600),List.of(4,2))).activity();
        UUID group=a.groups().getFirst().id();join(p,a,0,0,UUID.randomUUID());
        UUID memberRequest=applications.apply(()->p+"-s1",course,group,UUID.randomUUID()).items().getFirst().id();
        applications.decide(()->p+"-s0",course,memberRequest,UUID.randomUUID(),"APPROVE");
        UUID rejectedKey=UUID.randomUUID();
        UUID rejected=applications.apply(()->p+"-s2",course,group,UUID.randomUUID()).items().getFirst().id();
        applications.decide(()->p+"-s0",course,rejected,rejectedKey,"REJECT");
        UUID pending=applications.apply(()->p+"-s2",course,group,UUID.randomUUID()).items().getFirst().id();
        UUID sendKey=UUID.randomUUID();var invitation=send(p,a,0,3,0,sendKey);
        var olderInvitation=send(p,a,0,4,0,UUID.randomUUID());
        String appCursor=applications.history(()->p+"-s0",course,null,1).nextCursor();
        String invCursor=invitations.history(()->p+"-s0",course,null,1).nextCursor();
        assertNotNull(appCursor);assertNotNull(invCursor);
        var originalApps=jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id",a.id());
        var originalInvs=jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id",a.id());
        jdbc.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",account(p+"-s1"),group);
        assertEquals(3,applications.history(()->p+"-s1",course,null,null).items().size());
        assertEquals(2,invitations.history(()->p+"-s1",course,null,null).items().size());
        assertTrue(applications.history(()->p+"-s0",course,null,null).items().isEmpty());
        assertTrue(invitations.history(()->p+"-s0",course,null,null).items().isEmpty());
        assertThrows(CourseNotAccessible.class,()->applications.decide(()->p+"-s0",course,pending,UUID.randomUUID(),"APPROVE"));
        assertThrows(CourseNotAccessible.class,()->send(p,a,0,5,0,UUID.randomUUID()));
        assertEquals(originalApps,jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id",a.id()));
        assertEquals(originalInvs,jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id",a.id()));
        applications.decide(()->p+"-s1",course,pending,UUID.randomUUID(),"APPROVE");
        assertEquals(account(p+"-s0"),jdbc.queryForObject("SELECT manager_account_id FROM application_attempt WHERE id=?",UUID.class,pending));
        assertEquals(account(p+"-s0"),jdbc.queryForObject("SELECT sender_account_id FROM invitation_attempt WHERE id=?",UUID.class,invitation.id()));
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());
        var appsBefore=jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id",a.id());
        var invsBefore=jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id",a.id());
        assertTrue(applications.history(()->p+"-s0",course,appCursor,1).items().isEmpty());
        assertTrue(invitations.history(()->p+"-s0",course,invCursor,1).items().isEmpty());
        assertTrue(applications.decide(()->p+"-s0",course,rejected,rejectedKey,"REJECT").items().isEmpty());
        assertTrue(invitations.invite(()->p+"-s0",course,group,account(p+"-s3"),sendKey).items().isEmpty());
        assertEquals(3,applications.history(()->p+"-s1",course,null,null).items().size());
        assertEquals(2,invitations.history(()->p+"-s1",course,null,null).items().size());
        assertEquals(invitation.id(),invitations.history(()->p+"-s3",course,null,null).items().getFirst().id());
        assertEquals(olderInvitation.id(),invitations.history(()->p+"-s4",course,null,null).items().getFirst().id());
        assertTrue(invitations.history(()->p+"-s2",course,null,null).items().isEmpty());
        assertTrue(applications.history(()->p+"-s5",course,null,null).items().isEmpty());
        assertEquals(appsBefore,jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id",a.id()));
        assertEquals(invsBefore,jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id",a.id()));
    }
    @Test void formerManagerKeepsOnlyOwnApplicantAndRecipientHistory(){
        String p="v2-own-history";UUID course=setup(p,6);
        var a=create(p,course,UUID.randomUUID(),new GroupingActivities.CreateActivity("Synthetic",Instant.now().plusSeconds(3600),List.of(4,2))).activity();
        join(p,a,0,0,UUID.randomUUID());join(p,a,3,1,UUID.randomUUID());UUID group=a.groups().getFirst().id();
        UUID member=applications.apply(()->p+"-s1",course,group,UUID.randomUUID()).items().getFirst().id();
        applications.decide(()->p+"-s0",course,member,UUID.randomUUID(),"APPROVE");
        send(p,a,0,2,0,UUID.randomUUID());
        jdbc.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",account(p+"-s1"),group);
        jdbc.update("DELETE FROM activity_membership WHERE activity_id=? AND account_id=?",a.id(),account(p+"-s0"));
        UUID own=applications.apply(()->p+"-s0",course,a.groups().get(1).id(),UUID.randomUUID()).items().getFirst().id();
        var received=send(p,a,3,0,1,UUID.randomUUID());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());
        assertEquals(List.of(own),applications.history(()->p+"-s0",course,null,null).items().stream().map(GroupingApplications.ApplicationAttempt::id).toList());
        assertEquals(List.of(received.id()),invitations.history(()->p+"-s0",course,null,null).items().stream().map(GroupingInvitations.InvitationAttempt::id).toList());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM activity_membership WHERE activity_id=? AND account_id=?",a.id(),account(p+"-s1")));
    }
    @Test void separateRequestsCoexistAndRejectionIsIndependentWithNewRetainedAttempts(){
        String p="inv-history";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());
        var app=applications.apply(()->p+"-s1",a.courseId(),a.groups().getFirst().id(),UUID.randomUUID()).items().getFirst();
        UUID key=UUID.randomUUID();var x=send(p,a,0,1,0,key);
        assertEquals("PENDING",x.status());assertEquals(account(p+"-s0"),x.sender().accountId());assertEquals(account(p+"-s1"),x.recipient().accountId());
        assertEquals(1,memberships.discover(()->p+"-s1",a.courseId()).groups().getFirst().availablePlaces());
        assertEquals(x.id(),send(p,a,0,1,0,key).id());
        assertEquals("INVITATION_PENDING",assertThrows(GroupingConflict.class,()->send(p,a,0,1,0,UUID.randomUUID())).code());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,()->send(p,a,0,2,0,key)).code());
        UUID decisionKey=UUID.randomUUID();respond(p,a,1,x.id(),"REJECT",decisionKey);
        assertEquals("PENDING",state("application_attempt",app.id(),"status"));
        var second=send(p,a,0,1,0,UUID.randomUUID());assertNotEquals(x.id(),second.id());
        applications.decide(()->p+"-s0",a.courseId(),app.id(),UUID.randomUUID(),"REJECT");
        assertEquals("PENDING",state("invitation_attempt",second.id(),"status"));
        assertEquals(2,respond(p,a,1,x.id(),"REJECT",decisionKey).items().size());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(GroupingConflict.class,()->respond(p,a,1,x.id(),"ACCEPT",decisionKey)).code());
        assertEquals("INVITATION_NOT_PENDING",assertThrows(GroupingConflict.class,()->respond(p,a,1,x.id(),"REJECT",UUID.randomUUID())).code());
    }
    @Test void acceptanceAtomicallyPreservesUsedAttemptAndCleansBothRequestTypesInOrder(){
        String p="inv-clean";var a=create(p,setup(p,6),UUID.randomUUID(),new GroupingActivities.CreateActivity("Synthetic",Instant.now().plusSeconds(3600),List.of(2,2,2))).activity();
        join(p,a,0,0,UUID.randomUUID());join(p,a,1,1,UUID.randomUUID());
        var used=send(p,a,0,2,0,UUID.randomUUID());var other=send(p,a,1,2,1,UUID.randomUUID());var leftover=send(p,a,0,3,0,UUID.randomUUID());var untouched=send(p,a,1,3,1,UUID.randomUUID());
        var counterpart=applications.apply(()->p+"-s2",a.courseId(),used.groupId(),UUID.randomUUID()).items().getFirst();
        var fullApp=applications.apply(()->p+"-s3",a.courseId(),used.groupId(),UUID.randomUUID()).items().getFirst();
        respond(p,a,2,used.id(),"ACCEPT",UUID.randomUUID());
        assertEquals("ACCEPTED",state("invitation_attempt",used.id(),"status"));assertEquals("Joined a group",state("application_attempt",counterpart.id(),"reason"));
        assertEquals("Joined a group",state("invitation_attempt",other.id(),"reason"));assertEquals("Group full",state("invitation_attempt",leftover.id(),"reason"));assertEquals("Group full",state("application_attempt",fullApp.id(),"reason"));assertEquals("PENDING",state("invitation_attempt",untouched.id(),"status"));
        join(p,a,3,2,UUID.randomUUID());assertEquals("Joined a group",state("invitation_attempt",untouched.id(),"reason"));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?",Integer.class,a.id(),used.groupId()));
    }
    @Test void applicationApprovalCancelsReceivedInvitationsButRetainsCompletedHistoryAndOtherActivities(){
        String p="inv-approval";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());var rejected=send(p,a,0,1,0,UUID.randomUUID());respond(p,a,1,rejected.id(),"REJECT",UUID.randomUUID());var pending=send(p,a,0,1,0,UUID.randomUUID());
        var foreign=fixture("inv-other-activity");join("inv-other-activity",foreign,0,0,UUID.randomUUID());var foreignInvitation=send("inv-other-activity",foreign,0,1,0,UUID.randomUUID());
        var app=applications.apply(()->p+"-s1",a.courseId(),a.groups().getFirst().id(),UUID.randomUUID()).items().getFirst();applications.decide(()->p+"-s0",a.courseId(),app.id(),UUID.randomUUID(),"APPROVE");
        assertEquals("Joined a group",state("invitation_attempt",pending.id(),"reason"));assertEquals("REJECTED",state("invitation_attempt",rejected.id(),"status"));assertEquals("PENDING",state("invitation_attempt",foreignInvitation.id(),"status"));
    }
    @Test void everyHistoryPageIsPrivateBoundToActorCourseAndTypeAndReadsNeverWrite(){
        String p="inv-pages";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());var expected=new HashSet<UUID>();
        for(int i=0;i<7;i++){var x=send(p,a,0,1,0,UUID.randomUUID());expected.add(x.id());respond(p,a,1,x.id(),"REJECT",UUID.randomUUID());}expected.add(send(p,a,0,1,0,UUID.randomUUID()).id());
        var first=invitations.history(()->p+"-s1",a.courseId(),null,2);String bound=first.nextCursor();assertNotNull(bound);
        assertThrows(GroupingValidationException.class,()->invitations.history(()->p+"-s2",a.courseId(),bound,2));assertThrows(GroupingValidationException.class,()->applications.history(()->p+"-s1",a.courseId(),bound,2));
        var foreign=fixture("inv-page-other");assertThrows(CourseNotAccessible.class,()->invitations.history(()->p+"-s1",foreign.courseId(),bound,2));
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());var before=jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id",a.id());
        var seen=new HashSet<UUID>();String cursor=null;do{var page=invitations.history(()->p+"-s1",a.courseId(),cursor,2);assertEquals("FINALIZING",page.status());for(var x:page.items()){assertTrue(seen.add(x.id()));assertTrue(Set.of("EXPIRED","REJECTED").contains(x.status()));}cursor=page.nextCursor();}while(cursor!=null);
        assertEquals(expected,seen);assertEquals(before,jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id",a.id()));assertTrue(invitations.history(()->p+"-s2",a.courseId(),null,null).items().isEmpty());
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",a.courseId(),account(p+"-s1"));assertThrows(CourseNotAccessible.class,()->invitations.history(()->p+"-s1",a.courseId(),bound,2));
    }
    @Test void cutoffStopsSendingAcceptanceAndRejectionWhileReplayReturnsCurrentSafeHistory(){
        String p="inv-cutoff";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());UUID key=UUID.randomUUID();var x=send(p,a,0,1,0,key);
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp() WHERE id=?",a.id());assertEquals("EXPIRED",send(p,a,0,1,0,key).status());
        assertEquals("ACTIVITY_CLOSED",assertThrows(GroupingConflict.class,()->send(p,a,0,2,0,UUID.randomUUID())).code());
        for(String decision:List.of("ACCEPT","REJECT"))assertEquals("ACTIVITY_CLOSED",assertThrows(GroupingConflict.class,()->respond(p,a,1,x.id(),decision,UUID.randomUUID())).code());
        assertEquals("PENDING",state("invitation_attempt",x.id(),"status"));jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?",a.courseId(),account(p+"-s0"));assertThrows(CourseNotAccessible.class,()->send(p,a,0,1,0,key));
    }
    @Test void rollbackIncludesMembershipAcceptedOutcomeCombinedCleanupAndReceipt(){
        String p="inv-rollback";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());var x=send(p,a,0,1,0,UUID.randomUUID());var app=applications.apply(()->p+"-s1",a.courseId(),x.groupId(),UUID.randomUUID()).items().getFirst();UUID key=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION fail_invitation_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic rollback'; END $$");jdbc.execute("CREATE TRIGGER fail_invitation_cleanup BEFORE UPDATE ON application_attempt FOR EACH ROW WHEN (NEW.status='CANCELED') EXECUTE FUNCTION fail_invitation_cleanup()");
        try{assertThrows(org.springframework.dao.DataAccessException.class,()->respond(p,a,1,x.id(),"ACCEPT",key));}finally{jdbc.execute("DROP TRIGGER fail_invitation_cleanup ON application_attempt");jdbc.execute("DROP FUNCTION fail_invitation_cleanup()");}
        assertEquals("PENDING",state("invitation_attempt",x.id(),"status"));assertEquals("PENDING",state("application_attempt",app.id(),"status"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?",Integer.class,a.id()));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));respond(p,a,1,x.id(),"ACCEPT",key);
    }
    @Test void httpSessionCsrfStrictDtosRecipientOnlyAndPrivateHistory()throws Exception{
        String p="inv-http";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());String base="/api/courses/"+a.courseId(),path=base+"/groups/"+a.groups().getFirst().id()+"/invitations";var manager=login(p+"-s0");var recipient=login(p+"-s1");String body=mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID(),"recipientId",account(p+"-s1")));
        assertEquals(401,get(client(),base+"/invitations").statusCode());assertEquals(403,request(manager,path,"POST",body,null,"application/json").statusCode());
        for(String invalid:List.of("{}","null","[]","{broken","{\"commandKey\":true}","{\"commandKey\":\"1-1-1-1-1\",\"recipientId\":\""+account(p+"-s1")+"\"}","{\"commandKey\":\""+UUID.randomUUID()+"\",\"recipientId\":true}",body.substring(0,body.length()-1)+",\"status\":\"ACCEPTED\"}"))assertEquals(400,request(manager,path,"POST",invalid,csrf(manager),"application/json").statusCode());
        for(Object invalidRecipient:List.of(true,"1-1-1-1-1","not-a-uuid",List.of(account(p+"-s1"))))assertEquals(400,request(manager,path,"POST",mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID(),"recipientId",invalidRecipient)),csrf(manager),"application/json").statusCode());
        assertEquals(400,request(manager,path,"POST",mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID())),csrf(manager),"application/json").statusCode());
        assertEquals(404,request(recipient,path,"POST",body,csrf(recipient),"application/json").statusCode());var response=request(manager,path,"POST",body,csrf(manager),"application/json");assertEquals(200,response.statusCode(),response.body());assertFalse(response.body().contains("members"));assertFalse(response.body().contains("ownGroupId"));UUID id=UUID.fromString(JsonPath.read(response.body(),"$.items[0].id"));
        String decision=base+"/invitations/"+id+"/decision",accept=mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID(),"decision","ACCEPT"));
        assertEquals(403,get(login(p+"-teacher"),base+"/invitations").statusCode());assertEquals(404,get(login(p+"-s4"),base+"/invitations").statusCode());assertFalse(get(login(p+"-s2"),base+"/invitations").body().contains(id.toString()));
        assertEquals(404,request(manager,decision,"POST",accept,csrf(manager),"application/json").statusCode());assertEquals(403,request(recipient,decision,"POST",accept,null,"application/json").statusCode());
        for(String bad:List.of("APPROVE","CANCEL"))assertEquals(400,request(recipient,decision,"POST",mapper.writeValueAsString(Map.of("commandKey",UUID.randomUUID(),"decision",bad)),csrf(recipient),"application/json").statusCode());
        String extreme=Base64.getUrlEncoder().withoutPadding().encodeToString(("invitation|"+account(p+"-s1")+"|"+a.courseId()+"|"+Instant.MAX+"|"+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String applicationCursor=Base64.getUrlEncoder().withoutPadding().encodeToString((account(p+"-s1")+"|"+a.courseId()+"|"+Instant.now()+"|"+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(String query:List.of("?cursor=bad","?cursor="+extreme,"?cursor="+applicationCursor,"?limit=0","?limit=51","?limit=oops"))assertEquals(400,get(recipient,base+"/invitations"+query).statusCode());
        assertEquals(200,request(recipient,decision,"POST",accept,csrf(recipient),"application/json").statusCode());
    }
    @Test void constraintsProtectPendingPairSameActivitySenderRecipientAndReasons(){
        String p="inv-constraints";var a=fixture(p);join(p,a,0,0,UUID.randomUUID());var x=send(p,a,0,1,0,UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO invitation_attempt(id,activity_id,group_id,recipient_account_id,sender_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),a.id(),x.groupId(),account(p+"-s1"),account(p+"-s0")));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE invitation_attempt SET status='CANCELED',reason=NULL WHERE id=?",x.id()));assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE invitation_attempt SET reason='Group full' WHERE id=?",x.id()));assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE invitation_attempt SET sender_account_id=? WHERE id=?",account(p+"-s4"),x.id()));assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE invitation_attempt SET recipient_account_id=? WHERE id=?",account(p+"-s4"),x.id()));
        var foreign=fixture("inv-foreign");assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE invitation_attempt SET group_id=? WHERE id=?",foreign.groups().getFirst().id(),x.id()));
    }
    @Test void populatedV9UpgradePreservesActorsOutcomesAndCurrentManagerIntegrity(){
        String schema="slice7_upgrade";jdbc.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("9").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl()+(POSTGRES.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,POSTGRES.getUsername(),POSTGRES.getPassword()));
        UUID student=UUID.randomUUID(),course=UUID.randomUUID(),activity=UUID.randomUUID(),group=UUID.randomUUID();
        db.update("INSERT INTO app_account VALUES (?,'preserved-app-student','hash','STUDENT')",student);
        db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'000陳😀','Preserved')",student);
        db.update("INSERT INTO course VALUES (?,'UPGRADE7','Preserved')",course);db.update("INSERT INTO course_enrollment VALUES (?,?)",course,student);
        db.update("INSERT INTO course_introduction VALUES (?,?,'Preserved 😀')",course,student);
        db.update("INSERT INTO grouping_activity(id,course_id,instructions,deadline) VALUES (?,?,'Instructions',clock_timestamp()+interval '1 hour')",activity,course);
        db.update("INSERT INTO activity_participant VALUES (?,?)",activity,student);db.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,1,2)",group,activity);
        db.update("INSERT INTO activity_membership VALUES (?,?,?)",activity,student,group);db.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",student,group);
        db.update("INSERT INTO grouping_command_receipt VALUES (?,'JOIN_EMPTY_GROUP',?,'preserved-fingerprint',?)",student,UUID.randomUUID(),activity);
        UUID recipient=UUID.randomUUID();db.update("INSERT INTO app_account VALUES (?,'preserved-inv-recipient','hash','STUDENT')",recipient);db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'000Recipient','Recipient')",recipient);db.update("INSERT INTO course_enrollment VALUES (?,?)",course,recipient);db.update("INSERT INTO activity_participant VALUES (?,?)",activity,recipient);
        UUID rejected=UUID.randomUUID();db.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'REJECTED')",rejected,activity,group,recipient,student);db.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),activity,group,recipient,student);
        UUID invitation=UUID.randomUUID();
        db.update("INSERT INTO invitation_attempt(id,activity_id,group_id,recipient_account_id,sender_account_id,status) VALUES (?,?,?,?,?,'REJECTED')",invitation,activity,group,recipient,student);
        db.update("INSERT INTO invitation_attempt(id,activity_id,group_id,recipient_account_id,sender_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),activity,group,recipient,student);
        var tables=List.of("application_attempt","invitation_attempt","app_account","student_identity","course","course_enrollment","course_introduction","activity_participant","activity_group","activity_membership","grouping_command_receipt");
        var before=new HashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,db.queryForList("SELECT * FROM "+table));
        var activityBefore=db.queryForList("SELECT id,course_id,instructions,deadline FROM grouping_activity");
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table));
        assertEquals(activityBefore,db.queryForList("SELECT id,course_id,instructions,deadline FROM grouping_activity"));
        for(var row:db.queryForList("SELECT finalized_at FROM grouping_activity"))assertNull(row.get("finalized_at"));
        assertEquals(11,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
        assertEquals("11",db.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",String.class));
        assertThrows(DataIntegrityViolationException.class,()->db.update("DELETE FROM activity_membership WHERE activity_id=? AND account_id=?",activity,student));
        db.update("UPDATE activity_group SET manager_account_id=NULL WHERE id=?",group);
        assertEquals(1,db.update("DELETE FROM activity_membership WHERE activity_id=? AND account_id=?",activity,student));
        for(String table:List.of("application_attempt","invitation_attempt")){
            assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table));
            db.update("UPDATE "+table+" SET status='CANCELED',reason='Group empty' WHERE status='PENDING'");
            assertEquals(1,db.queryForObject("SELECT count(*) FROM "+table+" WHERE reason='Group empty'",Integer.class));
            assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE "+table+" SET reason='Other' WHERE status='CANCELED'"));
        }
        assertThrows(DataIntegrityViolationException.class,()->db.update("DELETE FROM activity_participant WHERE activity_id=? AND account_id=?",activity,student));
    }
    @Test void v6UpgradePreservesApplicationsMembershipManagerProfilesHistoryAndReceipts(){
        String schema="slice7_upgrade_legacy";jdbc.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("6").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl()+(POSTGRES.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,POSTGRES.getUsername(),POSTGRES.getPassword()));
        UUID student=UUID.randomUUID(),course=UUID.randomUUID(),activity=UUID.randomUUID(),group=UUID.randomUUID();
        db.update("INSERT INTO app_account VALUES (?,'preserved-app-student','hash','STUDENT')",student);
        db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'000陳😀','Preserved')",student);
        db.update("INSERT INTO course VALUES (?,'UPGRADE7','Preserved')",course);db.update("INSERT INTO course_enrollment VALUES (?,?)",course,student);
        db.update("INSERT INTO course_introduction VALUES (?,?,'Preserved 😀')",course,student);
        db.update("INSERT INTO grouping_activity VALUES (?,?,'Instructions',clock_timestamp()+interval '1 hour')",activity,course);
        db.update("INSERT INTO activity_participant VALUES (?,?)",activity,student);db.update("INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,1,2)",group,activity);
        db.update("INSERT INTO activity_membership VALUES (?,?,?)",activity,student,group);db.update("UPDATE activity_group SET manager_account_id=? WHERE id=?",student,group);
        db.update("INSERT INTO grouping_command_receipt VALUES (?,'JOIN_EMPTY_GROUP',?,'preserved-fingerprint',?)",student,UUID.randomUUID(),activity);
        UUID recipient=UUID.randomUUID();db.update("INSERT INTO app_account VALUES (?,'preserved-inv-recipient','hash','STUDENT')",recipient);db.update("INSERT INTO student_identity(account_id,student_id,student_name) VALUES (?,'000Recipient','Recipient')",recipient);db.update("INSERT INTO course_enrollment VALUES (?,?)",course,recipient);db.update("INSERT INTO activity_participant VALUES (?,?)",activity,recipient);
        UUID rejected=UUID.randomUUID();db.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'REJECTED')",rejected,activity,group,recipient,student);db.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),activity,group,recipient,student);
        var tables=List.of("application_attempt","app_account","student_identity","course","course_enrollment","course_introduction","activity_participant","activity_group","activity_membership","grouping_command_receipt");
        var before=new HashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,db.queryForList("SELECT * FROM "+table));
        var activityBefore=db.queryForList("SELECT id,course_id,instructions,deadline FROM grouping_activity");
        Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load().migrate();
        for(String table:tables)assertEquals(before.get(table),db.queryForList("SELECT * FROM "+table));
        assertEquals(activityBefore,db.queryForList("SELECT id,course_id,instructions,deadline FROM grouping_activity"));
        for(var row:db.queryForList("SELECT finalized_at FROM grouping_activity"))assertNull(row.get("finalized_at"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM invitation_attempt",Integer.class));assertEquals(11,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
    }
}
