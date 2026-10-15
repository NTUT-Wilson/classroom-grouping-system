package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.courses.api.ActivityCourseAccess;
import com.classroomgrouping.courses.api.CourseIntroductions;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.api.GroupingConflict;
import com.classroomgrouping.grouping.api.GroupingMemberships;
import com.classroomgrouping.grouping.api.GroupingValidationException;
import com.classroomgrouping.identity.api.StudentIdentities;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
class DatabaseGroupingMemberships implements GroupingMemberships {
    private final JdbcTemplate jdbc;
    private final GroupJoiningWriter joining;
    private final ActivityCourseAccess courses;
    private final StudentIdentities identities;
    private final CourseIntroductions introductions;
    DatabaseGroupingMemberships(JdbcTemplate jdbc, ActivityCourseAccess courses, StudentIdentities identities,
            CourseIntroductions introductions, GroupJoiningWriter joining) {
        this.joining = joining; this.jdbc = jdbc; this.courses = courses; this.identities = identities; this.introductions = introductions;
    }
    // Read one coherent roster/status snapshot; wall-clock cutoff checks still use clock_timestamp().
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Discovery discover(Principal principal, UUID courseId) {
        var access = student(principal, courseId);
        var activities = activity(courseId, false);
        if (activities.isEmpty()) return new Discovery(null, "ABSENT", null, List.of());
        var activity = activities.getFirst();
        participant(activity.id(), access.actorId());
        return project(courseId, activity, access.actorId());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Discovery joinEmpty(Principal principal, UUID courseId, UUID groupId, UUID commandKey) {
        // Every mutation serializes on its activity; all authority is reread after any lock wait.
        var activities = activity(courseId, true);
        var access = student(principal, courseId);
        if (activities.isEmpty()) throw new CourseNotAccessible();
        var activity = activities.getFirst();
        participant(activity.id(), access.actorId());
        if (commandKey == null) throw new GroupingValidationException("commandKey");
        String fingerprint = fingerprint(courseId, groupId);
        int claimed = jdbc.update("""
                INSERT INTO grouping_command_receipt(actor_id,command,command_key,fingerprint,activity_id)
                VALUES (?,'JOIN_EMPTY_GROUP',?,?,?) ON CONFLICT DO NOTHING
                """, access.actorId(), commandKey, fingerprint, activity.id());
        if (claimed == 0) {
            String saved = jdbc.queryForObject("""
                    SELECT fingerprint FROM grouping_command_receipt
                    WHERE actor_id=? AND command='JOIN_EMPTY_GROUP' AND command_key=?
                    """, String.class, access.actorId(), commandKey);
            if (!fingerprint.equals(saved)) throw new GroupingConflict("COMMAND_KEY_REUSED");
            return project(courseId, activity, access.actorId());
        }
        requireOpen(activity);
        var groups = jdbc.query("SELECT capacity FROM activity_group WHERE activity_id=? AND id=?",
                (rs, row) -> rs.getInt(1), activity.id(), groupId);
        if (groups.isEmpty()) throw new CourseNotAccessible();
        if (jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?",
                Integer.class, activity.id(), groupId) != 0) throw new GroupingConflict("GROUP_NOT_EMPTY");
        if (!jdbc.queryForList("SELECT group_id FROM activity_membership WHERE activity_id=? AND account_id=?",
                activity.id(), access.actorId()).isEmpty()) throw new GroupingConflict("ALREADY_GROUPED");
        joining.join(activity.id(), activity.deadline(), groupId, access.actorId(), null);
        return project(courseId, activity, access.actorId());
    }
    private ActivityCourseAccess.Access student(Principal principal, UUID courseId) {
        var access = courses.read(principal, courseId);
        if (access.teacher()) throw new CourseNotAccessible();
        return access;
    }
    private void participant(UUID activityId, UUID actorId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM activity_participant WHERE activity_id=? AND account_id=?)",
                Boolean.class, activityId, actorId))) throw new CourseNotAccessible();
    }
    private record Activity(UUID id, Instant deadline) {}
    private List<Activity> activity(UUID courseId, boolean lock) {
        return jdbc.query("SELECT id,deadline FROM grouping_activity WHERE course_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Activity(rs.getObject("id", UUID.class), rs.getTimestamp("deadline").toInstant()), courseId);
    }
    private boolean open(Activity activity) {
        return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant().isBefore(activity.deadline());
    }
    private void requireOpen(Activity activity) {
        if (!open(activity)) throw new GroupingConflict("ACTIVITY_CLOSED");
    }
    private Discovery closed(Activity activity) { return new Discovery(activity.id(), "FINALIZING", null, List.of()); }
    private record Membership(UUID accountId, UUID groupId) {}
    private Discovery project(UUID courseId, Activity activity, UUID actorId) {
        if (!open(activity)) return closed(activity);
        var memberships = jdbc.query("SELECT account_id,group_id FROM activity_membership WHERE activity_id=?",
                (rs, row) -> new Membership(rs.getObject("account_id", UUID.class), rs.getObject("group_id", UUID.class)), activity.id());
        var ids = memberships.stream().map(Membership::accountId).toList();
        // The participant/group FKs prove these are fixed participants of this authorized activity.
        var students = identities.lookup(ids);
        var aboutMe = introductions.lookup(courseId, ids);
        var groups = jdbc.query("SELECT id,ordinal,capacity,manager_account_id FROM activity_group WHERE activity_id=? ORDER BY ordinal",
                (rs, row) -> {
                    UUID groupId = rs.getObject("id", UUID.class);
                    var members = memberships.stream().filter(m -> groupId.equals(m.groupId())).map(m -> {
                        var identity = Objects.requireNonNull(students.get(m.accountId()), "Participant identity missing");
                        return new Profile(identity.accountId(), identity.studentId(), identity.studentName(), aboutMe.get(m.accountId()));
                    }).sorted(Comparator.comparing(Profile::studentId).thenComparing(Profile::accountId)).toList();
                    UUID managerId = rs.getObject("manager_account_id", UUID.class);
                    Profile manager = members.stream().filter(m -> m.accountId().equals(managerId)).findFirst().orElse(null);
                    return new Group(groupId, "Group " + rs.getInt("ordinal"), rs.getInt("capacity"),
                            rs.getInt("capacity") - members.size(), manager, members);
                }, activity.id());
        UUID ownGroup = memberships.stream().filter(m -> actorId.equals(m.accountId())).map(Membership::groupId).findFirst().orElse(null);
        // A projection spanning the cutoff must not return a partial roster or other groups' membership.
        if (!open(activity)) return closed(activity);
        return new Discovery(activity.id(), "OPEN", ownGroup, groups);
    }
    private static String fingerprint(UUID courseId, UUID groupId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (courseId.toString() + ":" + groupId.toString()).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint grouping command", impossible);
        }
    }
}
