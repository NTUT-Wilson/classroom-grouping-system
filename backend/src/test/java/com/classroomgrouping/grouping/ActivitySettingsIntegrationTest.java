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
class ActivitySettingsIntegrationTest {
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
    private GroupingActivities.Activity fixture(String prefix) {
        return create(prefix,setup(prefix,4),UUID.randomUUID(),valid()).activity();
    }
    private HttpResponse<String> settings(HttpClient c, UUID course, String suffix, String method, Map<String,Object> body) throws Exception {
        return request(c,"/api/courses/"+course+"/activity/"+suffix,method,mapper.writeValueAsString(body),csrf(c),"application/json");
    }
    @Test void textRulesAreSharedAndCutoffDoesNotPreventInstructions() {
        var a=fixture("settings-text");
        var groups=jdbc.queryForList("SELECT * FROM activity_group WHERE activity_id=? ORDER BY ordinal",a.id());
        var participants=jdbc.queryForList("SELECT * FROM activity_participant WHERE activity_id=? ORDER BY account_id",a.id());
        for(String text:List.of(""," \r\n\t","\u00a0\u0085\ufeff","x".repeat(5001),"😀".repeat(5001),"x\u0000x","\ud800","\udc00"))
            assertThrows(ActivityValidationException.class,()->activities.updateInstructions(()->"settings-text-teacher",a.courseId(),UUID.randomUUID(),text));
        assertThrows(ActivityValidationException.class,()->activities.updateInstructions(()->"settings-text-teacher",a.courseId(),null,"new"));
        assertEquals(a.instructions(),activities.workspace(()->"settings-text-teacher",a.courseId()).activity().instructions());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",a.id());
        var deadline=activities.workspace(()->"settings-text-teacher",a.courseId()).activity().deadline();
        String text="😀".repeat(5000);
        var edited=activities.updateInstructions(()->"settings-text-teacher",a.courseId(),UUID.randomUUID(),text).activity();
        assertEquals(text,edited.instructions()); assertEquals(deadline,edited.deadline()); assertEquals("FINALIZING",edited.status());
        assertEquals(text,activities.workspace(()->"settings-text-s0",a.courseId()).activity().instructions());
        assertEquals(groups,jdbc.queryForList("SELECT * FROM activity_group WHERE activity_id=? ORDER BY ordinal",a.id()));
        assertEquals(participants,jdbc.queryForList("SELECT * FROM activity_participant WHERE activity_id=? ORDER BY account_id",a.id()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE activity_id=?",Integer.class,a.id()));
    }
    @Test void extensionsUseStoredPrecisionAndNeverReopen() {
        var a=fixture("settings-time");
        for(var proposed:List.of(a.deadline(),a.deadline().minusSeconds(1),a.deadline().plusNanos(1)))
            assertEquals("DEADLINE_NOT_LATER",assertThrows(ActivityConflict.class,()->activities.extendDeadline(()->"settings-time-teacher",a.courseId(),UUID.randomUUID(),proposed)).code());
        assertThrows(ActivityValidationException.class,()->activities.extendDeadline(()->"settings-time-teacher",a.courseId(),UUID.randomUUID(),null));
        assertThrows(ActivityValidationException.class,()->activities.extendDeadline(()->"settings-time-teacher",a.courseId(),UUID.randomUUID(),Instant.MAX));
        var proposed=a.deadline().plusSeconds(60).plusNanos(999);
        var extended=activities.extendDeadline(()->"settings-time-teacher",a.courseId(),UUID.randomUUID(),proposed).activity();
        assertEquals(proposed.truncatedTo(java.time.temporal.ChronoUnit.MICROS),extended.deadline());
        assertEquals(a.instructions(),extended.instructions()); assertEquals(a.groups(),extended.groups());
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",a.id());
        var before=jdbc.queryForMap("SELECT * FROM grouping_activity WHERE id=?",a.id());
        assertEquals("ACTIVITY_CLOSED",assertThrows(ActivityConflict.class,()->activities.extendDeadline(()->"settings-time-teacher",a.courseId(),UUID.randomUUID(),Instant.now().plusSeconds(7200))).code());
        assertEquals(before,jdbc.queryForMap("SELECT * FROM grouping_activity WHERE id=?",a.id()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE activity_id=?",Integer.class,a.id()));
    }
    @Test void replayReturnsCurrentProjectionAndReauthorizesWithoutRestoringOldValues() {
        var a=fixture("settings-replay"); var key=UUID.randomUUID(); var later=a.deadline().plusSeconds(60);
        activities.updateInstructions(()->"settings-replay-teacher",a.courseId(),key,"First");
        activities.extendDeadline(()->"settings-replay-teacher",a.courseId(),key,later);
        activities.updateInstructions(()->"settings-replay-teacher",a.courseId(),UUID.randomUUID(),"Current <plain> 😀");
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",a.id());
        var current=activities.workspace(()->"settings-replay-teacher",a.courseId());
        assertEquals(current,activities.updateInstructions(()->"settings-replay-teacher",a.courseId(),key,"First"));
        assertEquals(current,activities.extendDeadline(()->"settings-replay-teacher",a.courseId(),key,later));
        assertEquals("COMMAND_KEY_REUSED",assertThrows(ActivityConflict.class,()->activities.updateInstructions(()->"settings-replay-teacher",a.courseId(),key,"First ")).code());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(ActivityConflict.class,()->activities.extendDeadline(()->"settings-replay-teacher",a.courseId(),key,later.plusNanos(1))).code());
        var other=fixture("settings-replay-other");
        jdbc.update("INSERT INTO course_teacher_assignment(course_id,account_id) SELECT ?,id FROM app_account WHERE username='settings-replay-teacher'",other.courseId());
        assertEquals("COMMAND_KEY_REUSED",assertThrows(ActivityConflict.class,()->activities.updateInstructions(()->"settings-replay-teacher",other.courseId(),key,"First")).code());
        jdbc.update("DELETE FROM course_teacher_assignment WHERE course_id=?",a.courseId());
        assertThrows(CourseNotAccessible.class,()->activities.updateInstructions(()->"settings-replay-teacher",a.courseId(),key,"First"));
        assertThrows(CourseNotAccessible.class,()->activities.extendDeadline(()->"settings-replay-teacher",a.courseId(),key,later));
    }
    @Test void httpRejectsInjectionWrongTypesCsrfAndDeniedActors() throws Exception {
        var a=fixture("settings-http"); var c=login("settings-http-teacher");
        for(String suffix:List.of("instructions","deadline")) {
            String method=suffix.equals("instructions")?"PUT":"POST";
            var input=new HashMap<String,Object>(); input.put("commandKey",UUID.randomUUID());
            input.put(suffix,suffix.equals("instructions")?"Updated <plain> 😀":a.deadline().plusSeconds(60).toString());
            String path="/api/courses/"+a.courseId()+"/activity/"+suffix;
            var anonymous=client();
            assertEquals(401,request(anonymous,path,method,mapper.writeValueAsString(input),csrf(anonymous),"application/json").statusCode());
            for(String user:List.of("settings-http-s0","settings-http-other","settings-http-s4"))
                assertEquals(user.endsWith("-other")?404:403,settings(login(user),a.courseId(),suffix,method,input).statusCode());
            for(String token:List.of("","invalid"))
                assertEquals(403,request(c,path,method,mapper.writeValueAsString(input),token.isEmpty()?null:token,"application/json").statusCode());
            for(String injected:List.of("capacities","participants","groups","courseId","memberships","status","allocationPlan",suffix.equals("instructions")?"deadline":"instructions")) {
                var bad=new HashMap<>(input);bad.put(injected,"injected");
                var response=settings(c,a.courseId(),suffix,method,bad);
                assertEquals(400,response.statusCode(),response.body()); assertFalse(response.body().contains("injected"));
            }
            for(Object invalidKey:List.of(42,true,"not-a-uuid","1-1-1-1-1")) {
                var bad=new HashMap<>(input); bad.put("commandKey",invalidKey);
                assertEquals(400,settings(c,a.courseId(),suffix,method,bad).statusCode());
            }
            for(String missing:List.of("commandKey",suffix)) {
                var bad=new HashMap<>(input); bad.remove(missing);
                assertEquals(400,settings(c,a.courseId(),suffix,method,bad).statusCode());
            }
            for(Object invalid:List.of(42,true,List.of("x"),Map.of("x","y"))) {
                var bad=new HashMap<>(input);bad.put(suffix,invalid);
                assertEquals(400,settings(c,a.courseId(),suffix,method,bad).statusCode());
            }
            for(String invalid:List.of("[]","null","{broken"))
                assertEquals(400,request(c,path,method,invalid,csrf(c),"application/json").statusCode());
            if(suffix.equals("deadline")) {
                input.put(suffix,"2030-01-01T12:00:00");
                assertEquals(400,settings(c,a.courseId(),suffix,method,input).statusCode());
            }
        }
        assertEquals(a,activities.workspace(()->"settings-http-teacher",a.courseId()).activity());
        assertThrows(CourseNotAccessible.class,()->activities.updateInstructions(()->"settings-http-s0",a.courseId(),UUID.randomUUID(),"bad"));
        assertThrows(CourseNotAccessible.class,()->activities.extendDeadline(()->"settings-http-s0",a.courseId(),UUID.randomUUID(),a.deadline().plusSeconds(60)));
        assertThrows(CourseNotAccessible.class,()->activities.updateInstructions(()->"settings-http-teacher",UUID.randomUUID(),UUID.randomUUID(),"bad"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE activity_id=?",Integer.class,a.id()));
        assertEquals(200,settings(c,a.courseId(),"instructions","PUT",Map.of("commandKey",UUID.randomUUID(),"instructions","HTTP saved 😀")).statusCode());
        assertEquals(200,settings(c,a.courseId(),"deadline","POST",Map.of("commandKey",UUID.randomUUID(),"deadline",a.deadline().plusSeconds(60).toString())).statusCode());
        assertEquals(409,settings(c,a.courseId(),"deadline","POST",Map.of("commandKey",UUID.randomUUID(),"deadline",a.deadline().toString())).statusCode());
    }
    @Test void readOnlyStatusAndMidWriteRollbackPreserveRecords() {
        var a=fixture("settings-atomic"); var key=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_settings_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_settings_update BEFORE UPDATE ON grouping_activity FOR EACH ROW EXECUTE FUNCTION reject_settings_update()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,()->activities.updateInstructions(()->"settings-atomic-teacher",a.courseId(),key,"changed"));
            assertThrows(org.springframework.dao.DataAccessException.class,()->activities.extendDeadline(()->"settings-atomic-teacher",a.courseId(),key,a.deadline().plusSeconds(60)));
        } finally { jdbc.execute("DROP TRIGGER reject_settings_update ON grouping_activity"); jdbc.execute("DROP FUNCTION reject_settings_update()"); }
        assertEquals(a,activities.workspace(()->"settings-atomic-teacher",a.courseId()).activity());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM grouping_command_receipt WHERE command_key=?",Integer.class,key));
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",a.id());
        var tables=List.of("grouping_activity","activity_participant","activity_group","grouping_command_receipt");
        var before=new HashMap<String,List<Map<String,Object>>>();for(String table:tables)before.put(table,jdbc.queryForList("SELECT * FROM "+table));
        for(int i=0;i<3;i++) for(String user:List.of("settings-atomic-teacher","settings-atomic-s0"))
            assertEquals("FINALIZING",activities.workspace(()->user,a.courseId()).activity().status());
        for(String table:tables)assertEquals(before.get(table),jdbc.queryForList("SELECT * FROM "+table));
    }
}
