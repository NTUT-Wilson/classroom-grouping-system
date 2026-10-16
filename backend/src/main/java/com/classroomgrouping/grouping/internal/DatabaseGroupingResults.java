package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.courses.api.ActivityCourseAccess;
import com.classroomgrouping.courses.api.CourseIntroductions;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.api.GroupingResults;
import com.classroomgrouping.grouping.api.ResultsUnavailable;
import com.classroomgrouping.identity.api.StudentIdentities;
import java.security.Principal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class DatabaseGroupingResults implements GroupingResults {
    private final JdbcTemplate jdbc;
    private final ActivityCourseAccess courses;
    private final StudentIdentities identities;
    private final CourseIntroductions introductions;
    DatabaseGroupingResults(JdbcTemplate jdbc, ActivityCourseAccess courses, StudentIdentities identities,
            CourseIntroductions introductions) {
        this.jdbc = jdbc; this.courses = courses; this.identities = identities; this.introductions = introductions;
    }
    public Result roster(Principal principal, UUID courseId) { return read(principal, courseId, true); }
    public Result myGroup(Principal principal, UUID courseId) { return read(principal, courseId, false); }
    private record Activity(UUID id, String status) {}
    private record StoredGroup(UUID id, int ordinal, int capacity, UUID manager) {}
    private Result read(Principal principal, UUID course, boolean teacher) {
        // All module calls join this same read-only PostgreSQL snapshot, including current identity and enrollment.
        var access = courses.read(principal, course);
        if (access.teacher() != teacher) throw new CourseNotAccessible();
        var activities = jdbc.query("""
                SELECT id, CASE WHEN deadline > clock_timestamp() THEN 'OPEN'
                    WHEN finalized_at IS NOT NULL THEN 'FINALIZED' ELSE 'FINALIZING' END
                FROM grouping_activity WHERE course_id=?
                """, (rs, n) -> new Activity(rs.getObject(1, UUID.class), rs.getString(2)), course);
        if (activities.isEmpty()) return new Result(null, "ABSENT", List.of());
        var activity = activities.getFirst();
        var participants = new HashSet<>(jdbc.queryForList(
                "SELECT account_id FROM activity_participant WHERE activity_id=?", UUID.class, activity.id()));
        if (!teacher && !participants.contains(access.actorId())) throw new CourseNotAccessible();
        if (!"FINALIZED".equals(activity.status())) return new Result(activity.id(), activity.status(), List.of());
        var groups = jdbc.query("SELECT id,ordinal,capacity,manager_account_id FROM activity_group WHERE activity_id=? ORDER BY ordinal",
                (rs, n) -> new StoredGroup(rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3), rs.getObject(4, UUID.class)), activity.id());
        var memberships = mapping("activity_membership", activity.id());
        var placements = mapping("allocation_placement", activity.id());
        var plans = jdbc.queryForList("SELECT state,placement_count FROM allocation_plan WHERE activity_id=?", activity.id());
        require(plans.size() == 1 && "COMPLETE".equals(plans.getFirst().get("state"))
                && ((Number) plans.getFirst().get("placement_count")).intValue() == placements.size());
        require(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM allocation_work WHERE activity_id=? AND state='COMPLETE' AND stage='ASSIGNMENT')", Boolean.class, activity.id())));
        require(!groups.isEmpty() && groups.stream().mapToLong(StoredGroup::capacity).sum() == participants.size());
        require(memberships.keySet().equals(participants));
        var groupIds = groups.stream().map(StoredGroup::id).collect(java.util.stream.Collectors.toSet());
        require(groupIds.containsAll(memberships.values()));
        require(placements.entrySet().stream().allMatch(e -> e.getValue().equals(memberships.get(e.getKey()))));
        require(jdbc.queryForObject("SELECT count(*) FROM application_attempt WHERE activity_id=? AND status='PENDING'", Integer.class, activity.id()) == 0);
        require(jdbc.queryForObject("SELECT count(*) FROM invitation_attempt WHERE activity_id=? AND status='PENDING'", Integer.class, activity.id()) == 0);
        for (var group : groups) {
            require(memberships.values().stream().filter(group.id()::equals).count() == group.capacity());
            if (group.manager() == null) require(memberships.entrySet().stream().filter(e -> group.id().equals(e.getValue()))
                    .allMatch(e -> group.id().equals(placements.get(e.getKey()))));
            else require(group.id().equals(memberships.get(group.manager())) && !placements.containsKey(group.manager()));
        }
        // Only the authorized projection's identities and introductions are fetched, after complete coverage is proven.
        UUID ownGroup = memberships.get(access.actorId());
        if (!teacher) require(ownGroup != null);
        var visible = groups.stream().filter(g -> teacher || g.id().equals(ownGroup)).toList();
        var ids = memberships.entrySet().stream().filter(e -> teacher || e.getValue().equals(ownGroup)).map(Map.Entry::getKey).toList();
        var students = identities.lookup(ids);
        require(students.keySet().containsAll(ids));
        var about = teacher ? Map.<UUID, String>of() : introductions.lookup(course, ids);
        var result = visible.stream().map(g -> {
            var members = ids.stream().filter(id -> g.id().equals(memberships.get(id))).map(id -> {
                var identity = students.get(id);
                return new Member(id, identity.studentId(), identity.studentName(), teacher ? null : about.getOrDefault(id, ""));
            }).sorted(Comparator.comparing(Member::studentId).thenComparing(Member::accountId)).toList();
            return new Group(g.id(), g.ordinal(), "Group " + g.ordinal(), g.capacity(), g.manager(), members);
        }).toList();
        return new Result(activity.id(), "FINALIZED", result);
    }
    private Map<UUID, UUID> mapping(String table, UUID activity) {
        var values = new HashMap<UUID, UUID>();
        jdbc.query("SELECT account_id,group_id FROM " + table + " WHERE activity_id=?", rs -> {
            require(values.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) == null);
        }, activity);
        return values;
    }
    private static void require(boolean condition) { if (!condition) throw new ResultsUnavailable(); }
}
