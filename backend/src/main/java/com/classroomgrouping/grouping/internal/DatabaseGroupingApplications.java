package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.courses.api.ActivityCourseAccess;
import com.classroomgrouping.courses.api.CourseIntroductions;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.api.*;
import com.classroomgrouping.identity.api.StudentIdentities;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.READ_COMMITTED)
class DatabaseGroupingApplications implements GroupingApplications {
    private final JdbcTemplate jdbc;
    private final ActivityCourseAccess courses;
    private final StudentIdentities identities;
    private final CourseIntroductions introductions;
    private final GroupJoiningWriter joining;
    DatabaseGroupingApplications(JdbcTemplate jdbc, ActivityCourseAccess courses, StudentIdentities identities,
            CourseIntroductions introductions, GroupJoiningWriter joining) {
        this.jdbc=jdbc;this.courses=courses;this.identities=identities;this.introductions=introductions;this.joining=joining;
    }
    private record Activity(UUID id, Instant deadline) {}
    private record Attempt(UUID id, UUID groupId, int ordinal, UUID applicant, UUID manager, String status, String reason, Instant createdAt) {}
    private record Cursor(Instant createdAt, UUID id) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ApplicationPage history(Principal principal, UUID courseId, String cursor, Integer limit) {
        UUID actor=student(principal,courseId);
        int size=limit==null?20:limit;
        if(size<1||size>50)throw new GroupingValidationException("limit");
        Cursor after=decode(cursor,actor,courseId);
        Activity activity=activity(courseId,false);
        if(activity==null)return new ApplicationPage(null,"ABSENT",List.of(),null);
        participant(activity.id(),actor);
        return project(courseId,activity,actor,after,size);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ApplicationPage apply(Principal principal, UUID courseId, UUID groupId, UUID commandKey) {
        Activity activity=activity(courseId,true);
        UUID actor=student(principal,courseId);
        if(activity==null)throw new CourseNotAccessible();
        participant(activity.id(),actor);
        if(replay(actor,"APPLY_TO_GROUP",commandKey,courseId+":"+groupId,activity.id()))return project(courseId,activity,actor,null,20);
        joining.requireOpen(activity.deadline());
        ungrouped(activity.id(),actor);
        var groups=jdbc.query("SELECT manager_account_id,capacity FROM activity_group WHERE activity_id=? AND id=?",
            (rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getInt(2)},activity.id(),groupId);
        if(groups.isEmpty())throw new CourseNotAccessible();
        UUID manager=(UUID)groups.getFirst()[0];
        int count=jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?",Integer.class,activity.id(),groupId);
        if(count==0||manager==null)throw new GroupingConflict("GROUP_NOT_MANAGED");
        if(count>=(int)groups.getFirst()[1])throw new GroupingConflict("GROUP_FULL");
        if(!jdbc.queryForList("SELECT 1 FROM application_attempt WHERE activity_id=? AND group_id=? AND applicant_account_id=? AND status='PENDING'",activity.id(),groupId,actor).isEmpty())
            throw new GroupingConflict("APPLICATION_PENDING");
        joining.requireOpen(activity.deadline());
        jdbc.update("INSERT INTO application_attempt(id,activity_id,group_id,applicant_account_id,manager_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),activity.id(),groupId,actor,manager);
        return project(courseId,activity,actor,null,20);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ApplicationPage decide(Principal principal, UUID courseId, UUID attemptId, UUID commandKey, String decision) {
        if(!"APPROVE".equals(decision)&&!"REJECT".equals(decision))throw new GroupingValidationException("decision");
        Activity activity=activity(courseId,true);
        UUID actor=student(principal,courseId);
        if(activity==null)throw new CourseNotAccessible();
        participant(activity.id(),actor);
        if(replay(actor,"DECIDE_APPLICATION",commandKey,courseId+":"+attemptId+":"+decision,activity.id()))return project(courseId,activity,actor,null,20);
        var attempts=jdbc.query("SELECT a.*,g.ordinal FROM application_attempt a JOIN activity_group g ON g.activity_id=a.activity_id AND g.id=a.group_id WHERE a.activity_id=? AND a.id=?",this::attempt,activity.id(),attemptId);
        if(attempts.isEmpty())throw new CourseNotAccessible();
        Attempt attempt=attempts.getFirst();
        UUID manager=jdbc.queryForObject("SELECT manager_account_id FROM activity_group WHERE activity_id=? AND id=?",UUID.class,activity.id(),attempt.groupId());
        if(!actor.equals(attempt.manager())||!actor.equals(manager))throw new CourseNotAccessible();
        if(!"PENDING".equals(attempt.status()))throw new GroupingConflict("APPLICATION_NOT_PENDING");
        joining.requireOpen(activity.deadline());
        if("APPROVE".equals(decision)){
            participant(activity.id(),attempt.applicant());
            // Current enrollment and role are required for the student being joined as well.
            if(!courses.isEnrolledStudent(courseId,attempt.applicant()))throw new CourseNotAccessible();
            joining.join(activity.id(),activity.deadline(),attempt.groupId(),attempt.applicant(),attempt.id());
        }else{
            joining.requireOpen(activity.deadline());
            jdbc.update("UPDATE application_attempt SET status='REJECTED' WHERE id=? AND activity_id=?",attempt.id(),activity.id());
        }
        return project(courseId,activity,actor,null,20);
    }
    private UUID student(Principal principal,UUID courseId){var access=courses.read(principal,courseId);if(access.teacher())throw new CourseNotAccessible();return access.actorId();}
    private void participant(UUID activity,UUID actor){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM activity_participant WHERE activity_id=? AND account_id=?)",Boolean.class,activity,actor)))throw new CourseNotAccessible();}
    private void ungrouped(UUID activity,UUID actor){if(!jdbc.queryForList("SELECT 1 FROM activity_membership WHERE activity_id=? AND account_id=?",activity,actor).isEmpty())throw new GroupingConflict("ALREADY_GROUPED");}
    private Activity activity(UUID course,boolean lock){var rows=jdbc.query("SELECT id,deadline FROM grouping_activity WHERE course_id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Activity(rs.getObject(1,UUID.class),rs.getTimestamp(2).toInstant()),course);return rows.isEmpty()?null:rows.getFirst();}
    private boolean replay(UUID actor,String command,UUID key,String fingerprint,UUID activity){
        if(key==null)throw new GroupingValidationException("commandKey");
        int inserted=jdbc.update("INSERT INTO grouping_command_receipt(actor_id,command,command_key,fingerprint,activity_id) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",actor,command,key,fingerprint,activity);
        if(inserted!=0)return false;
        String saved=jdbc.queryForObject("SELECT fingerprint FROM grouping_command_receipt WHERE actor_id=? AND command=? AND command_key=?",String.class,actor,command,key);
        if(!fingerprint.equals(saved))throw new GroupingConflict("COMMAND_KEY_REUSED");
        return true;
    }
    private Attempt attempt(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Attempt(rs.getObject("id",UUID.class),rs.getObject("group_id",UUID.class),rs.getInt("ordinal"),rs.getObject("applicant_account_id",UUID.class),rs.getObject("manager_account_id",UUID.class),rs.getString("status"),rs.getString("reason"),rs.getTimestamp("created_at").toInstant());}
    private ApplicationPage project(UUID course,Activity activity,UUID actor,Cursor after,int size){
        var args=new ArrayList<Object>(List.of(activity.id(),actor,actor));
        String anchor="";
        if(after!=null){anchor=" AND (a.created_at,a.id)<(?,?)";args.add(Timestamp.from(after.createdAt()));args.add(after.id());}
        args.add(size+1);
        var rows=jdbc.query("SELECT a.*,g.ordinal FROM application_attempt a JOIN activity_group g ON g.activity_id=a.activity_id AND g.id=a.group_id WHERE a.activity_id=? AND (a.applicant_account_id=? OR a.manager_account_id=?)"+anchor+" ORDER BY a.created_at DESC,a.id DESC LIMIT ?",this::attempt,args.toArray());
        boolean more=rows.size()>size;
        if(more)rows=rows.subList(0,size);
        var ids=rows.stream().flatMap(a->java.util.stream.Stream.of(a.applicant(),a.manager())).distinct().toList();
        var identitiesById=identities.lookup(ids);var about=introductions.lookup(course,ids);
        java.util.function.Function<UUID,GroupingMemberships.Profile> profile=id->{var i=Objects.requireNonNull(identitiesById.get(id));return new GroupingMemberships.Profile(i.accountId(),i.studentId(),i.studentName(),about.get(id));};
        boolean open=jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant().isBefore(activity.deadline());
        var items=rows.stream().map(a->new ApplicationAttempt(a.id(),a.groupId(),"Group "+a.ordinal(),profile.apply(a.applicant()),profile.apply(a.manager()),!open&&a.status().equals("PENDING")?"EXPIRED":a.status(),a.reason(),a.createdAt())).toList();
        String next=more?encode(actor,course,rows.getLast()):null;
        return new ApplicationPage(activity.id(),open?"OPEN":"FINALIZING",items,next);
    }
    private String encode(UUID actor,UUID course,Attempt a){return Base64.getUrlEncoder().withoutPadding().encodeToString((actor+"|"+course+"|"+a.createdAt()+"|"+a.id()).getBytes(StandardCharsets.UTF_8));}
    private Cursor decode(String cursor,UUID actor,UUID course){
        if(cursor==null)return null;
        try{if(cursor.length()>400)throw new IllegalArgumentException();String[] parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);if(parts.length!=4||!actor.toString().equals(parts[0])||!course.toString().equals(parts[1]))throw new IllegalArgumentException();Instant createdAt=Instant.parse(parts[2]);if(createdAt.isBefore(Instant.parse("-4712-01-01T00:00:00Z"))||!createdAt.isBefore(Instant.parse("+294277-01-01T00:00:00Z")))throw new IllegalArgumentException();return new Cursor(createdAt,UUID.fromString(parts[3]));}
        catch(RuntimeException invalid){throw new GroupingValidationException("cursor");}
    }
}
