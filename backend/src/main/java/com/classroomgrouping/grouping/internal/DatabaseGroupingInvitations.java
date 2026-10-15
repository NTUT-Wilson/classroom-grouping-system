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
class DatabaseGroupingInvitations implements GroupingInvitations {
    private final JdbcTemplate jdbc;
    private final ActivityCourseAccess courses;
    private final StudentIdentities identities;
    private final CourseIntroductions introductions;
    private final GroupJoiningWriter joining;
    DatabaseGroupingInvitations(JdbcTemplate jdbc, ActivityCourseAccess courses, StudentIdentities identities,
            CourseIntroductions introductions, GroupJoiningWriter joining) {
        this.jdbc=jdbc;this.courses=courses;this.identities=identities;this.introductions=introductions;this.joining=joining;
    }
    private record Activity(UUID id, Instant deadline) {}
    private record Attempt(UUID id, UUID groupId, int ordinal, UUID recipient, UUID sender, String status, String reason, Instant createdAt) {}
    private record Cursor(Instant createdAt, UUID id) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public InvitationPage history(Principal principal, UUID courseId, String cursor, Integer limit) {
        UUID actor=student(principal,courseId);
        int size=limit==null?20:limit;
        if(size<1||size>50)throw new GroupingValidationException("limit");
        Cursor after=decode(cursor,actor,courseId);
        Activity activity=activity(courseId,false);
        if(activity==null)return new InvitationPage(null,"ABSENT",List.of(),null);
        participant(activity.id(),actor);
        return project(courseId,activity,actor,after,size);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public InvitationPage invite(Principal principal, UUID courseId, UUID groupId, UUID recipientId, UUID commandKey) {
        Activity activity=activity(courseId,true);
        UUID actor=student(principal,courseId);
        if(activity==null)throw new CourseNotAccessible();
        participant(activity.id(),actor);
        if(replay(actor,"SEND_INVITATION",commandKey,courseId+":"+groupId+":"+recipientId,activity.id()))return project(courseId,activity,actor,null,20);
        joining.requireOpen(activity.deadline());
        if(recipientId==null)throw new GroupingValidationException("recipientId");
        var groups=jdbc.query("SELECT manager_account_id,capacity FROM activity_group WHERE activity_id=? AND id=?",
            (rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getInt(2)},activity.id(),groupId);
        if(groups.isEmpty())throw new CourseNotAccessible();
        UUID manager=(UUID)groups.getFirst()[0];
        if(!actor.equals(manager))throw new CourseNotAccessible();
        participant(activity.id(),recipientId);
        if(!courses.isEnrolledStudent(courseId,recipientId))throw new CourseNotAccessible();
        ungrouped(activity.id(),recipientId);
        int count=jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?",Integer.class,activity.id(),groupId);
        if(count==0||manager==null)throw new GroupingConflict("GROUP_NOT_MANAGED");
        if(count>=(int)groups.getFirst()[1])throw new GroupingConflict("GROUP_FULL");
        if(!jdbc.queryForList("SELECT 1 FROM invitation_attempt WHERE activity_id=? AND group_id=? AND recipient_account_id=? AND status='PENDING'",activity.id(),groupId,recipientId).isEmpty())
            throw new GroupingConflict("INVITATION_PENDING");
        joining.requireOpen(activity.deadline());
        jdbc.update("INSERT INTO invitation_attempt(id,activity_id,group_id,recipient_account_id,sender_account_id,status) VALUES (?,?,?,?,?,'PENDING')",UUID.randomUUID(),activity.id(),groupId,recipientId,actor);
        return project(courseId,activity,actor,null,20);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public InvitationPage decide(Principal principal, UUID courseId, UUID attemptId, UUID commandKey, String decision) {
        if(!"ACCEPT".equals(decision)&&!"REJECT".equals(decision))throw new GroupingValidationException("decision");
        Activity activity=activity(courseId,true);
        UUID actor=student(principal,courseId);
        if(activity==null)throw new CourseNotAccessible();
        participant(activity.id(),actor);
        if(replay(actor,"DECIDE_INVITATION",commandKey,courseId+":"+attemptId+":"+decision,activity.id()))return project(courseId,activity,actor,null,20);
        var attempts=jdbc.query("SELECT a.*,g.ordinal FROM invitation_attempt a JOIN activity_group g ON g.activity_id=a.activity_id AND g.id=a.group_id WHERE a.activity_id=? AND a.id=?",this::attempt,activity.id(),attemptId);
        if(attempts.isEmpty())throw new CourseNotAccessible();
        Attempt attempt=attempts.getFirst();
        if(!actor.equals(attempt.recipient()))throw new CourseNotAccessible();
        if(!"PENDING".equals(attempt.status()))throw new GroupingConflict("INVITATION_NOT_PENDING");
        joining.requireOpen(activity.deadline());
        if("ACCEPT".equals(decision)){
            participant(activity.id(),attempt.recipient());
            // Current enrollment and role are required for the student being joined as well.
            if(!courses.isEnrolledStudent(courseId,attempt.recipient()))throw new CourseNotAccessible();
            joining.joinInvitation(activity.id(),activity.deadline(),attempt.groupId(),attempt.recipient(),attempt.id());
        }else{
            joining.requireOpen(activity.deadline());
            jdbc.update("UPDATE invitation_attempt SET status='REJECTED' WHERE id=? AND activity_id=?",attempt.id(),activity.id());
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
    private Attempt attempt(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Attempt(rs.getObject("id",UUID.class),rs.getObject("group_id",UUID.class),rs.getInt("ordinal"),rs.getObject("recipient_account_id",UUID.class),rs.getObject("sender_account_id",UUID.class),rs.getString("status"),rs.getString("reason"),rs.getTimestamp("created_at").toInstant());}
    private InvitationPage project(UUID course,Activity activity,UUID actor,Cursor after,int size){
        var args=new ArrayList<Object>(List.of(activity.id(),actor,actor));
        String anchor="";
        if(after!=null){anchor=" AND (a.created_at,a.id)<(?,?)";args.add(Timestamp.from(after.createdAt()));args.add(after.id());}
        args.add(size+1);
        var rows=jdbc.query("SELECT a.*,g.ordinal FROM invitation_attempt a JOIN activity_group g ON g.activity_id=a.activity_id AND g.id=a.group_id WHERE a.activity_id=? AND (a.recipient_account_id=? OR a.sender_account_id=?)"+anchor+" ORDER BY a.created_at DESC,a.id DESC LIMIT ?",this::attempt,args.toArray());
        boolean more=rows.size()>size;
        if(more)rows=rows.subList(0,size);
        var ids=rows.stream().flatMap(a->java.util.stream.Stream.of(a.recipient(),a.sender())).distinct().toList();
        var identitiesById=identities.lookup(ids);var about=introductions.lookup(course,ids);
        java.util.function.Function<UUID,GroupingMemberships.Profile> profile=id->{var i=Objects.requireNonNull(identitiesById.get(id));return new GroupingMemberships.Profile(i.accountId(),i.studentId(),i.studentName(),about.get(id));};
        boolean open=jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant().isBefore(activity.deadline());
        var items=rows.stream().map(a->new InvitationAttempt(a.id(),a.groupId(),"Group "+a.ordinal(),profile.apply(a.sender()),profile.apply(a.recipient()),!open&&a.status().equals("PENDING")?"EXPIRED":a.status(),a.reason(),a.createdAt())).toList();
        String next=more?encode(actor,course,rows.getLast()):null;
        return new InvitationPage(activity.id(),open?"OPEN":"FINALIZING",items,next);
    }
    private String encode(UUID actor,UUID course,Attempt a){return Base64.getUrlEncoder().withoutPadding().encodeToString(("invitation|"+actor+"|"+course+"|"+a.createdAt()+"|"+a.id()).getBytes(StandardCharsets.UTF_8));}
    private Cursor decode(String cursor,UUID actor,UUID course){
        if(cursor==null)return null;
        try{if(cursor.length()>400)throw new IllegalArgumentException();String[] parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);if(parts.length!=5||!"invitation".equals(parts[0])||!actor.toString().equals(parts[1])||!course.toString().equals(parts[2]))throw new IllegalArgumentException();Instant createdAt=Instant.parse(parts[3]);if(createdAt.isBefore(Instant.parse("-4712-01-01T00:00:00Z"))||!createdAt.isBefore(Instant.parse("+294277-01-01T00:00:00Z")))throw new IllegalArgumentException();UUID id=UUID.fromString(parts[4]);if(!id.toString().equalsIgnoreCase(parts[4]))throw new IllegalArgumentException();return new Cursor(createdAt,id);}
        catch(RuntimeException invalid){throw new GroupingValidationException("cursor");}
    }
}
