package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.courses.api.ActivityCourseAccess;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.api.ActivityConflict;
import com.classroomgrouping.grouping.api.ActivityValidationException;
import com.classroomgrouping.grouping.api.GroupingActivities;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
class DatabaseGroupingActivities implements GroupingActivities {
    private final JdbcTemplate jdbc;
    private final ActivityCourseAccess courses;
    DatabaseGroupingActivities(JdbcTemplate jdbc, ActivityCourseAccess courses) { this.jdbc = jdbc; this.courses = courses; }
    public Workspace workspace(Principal principal, UUID courseId) {
        var access = courses.read(principal, courseId);
        return project(courseId, access);
    }
    private Workspace project(UUID courseId, ActivityCourseAccess.Access access) {
        var activities = jdbc.query("""
                SELECT id, instructions, deadline, CASE WHEN deadline > clock_timestamp() THEN 'OPEN' WHEN finalized_at IS NOT NULL THEN 'FINALIZED' ELSE 'FINALIZING' END AS status
                FROM grouping_activity WHERE course_id=?
                """, (rs, row) -> new Activity(rs.getObject("id", UUID.class), courseId, rs.getString("instructions"),
                rs.getTimestamp("deadline").toInstant(), rs.getString("status"), List.of()), courseId);
        if (activities.isEmpty()) return new Workspace(null, access.teacher() ? access.enrolledStudents().size() : null);
        var activity = activities.getFirst();
        if (!access.teacher() && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM activity_participant WHERE activity_id=? AND account_id=?)",
                Boolean.class, activity.id(), access.actorId()))) throw new CourseNotAccessible();
        var groups = jdbc.query("SELECT id, ordinal, capacity FROM activity_group WHERE activity_id=? ORDER BY ordinal",
                (rs, row) -> new Group(rs.getObject("id", UUID.class), rs.getInt("ordinal"),
                        "Group " + rs.getInt("ordinal"), rs.getInt("capacity")), activity.id());
        return new Workspace(new Activity(activity.id(), courseId, activity.instructions(), activity.deadline(), activity.status(), groups), null);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Workspace create(Principal principal, UUID courseId, UUID commandKey, CreateActivity request) {
        var access = courses.lockForCreation(principal, courseId);
        if (commandKey == null) throw new ActivityValidationException("commandKey");
        validate(request);
        // Copy mutable caller input before fingerprinting and use exactly that value throughout the transaction.
        request = new CreateActivity(request.instructions(), request.deadline(), List.copyOf(request.capacities()));
        String fingerprint = fingerprint(courseId, request);
        UUID activityId = UUID.randomUUID();
        int claimed = jdbc.update("""
                INSERT INTO grouping_command_receipt(actor_id, command, command_key, fingerprint, activity_id)
                VALUES (?, 'CREATE_ACTIVITY', ?, ?, ?) ON CONFLICT DO NOTHING
                """, access.actorId(), commandKey, fingerprint, activityId);
        if (claimed == 0) {
            var saved = jdbc.queryForMap("""
                    SELECT fingerprint, activity_id FROM grouping_command_receipt
                    WHERE actor_id=? AND command='CREATE_ACTIVITY' AND command_key=?
                    """, access.actorId(), commandKey);
            if (!fingerprint.equals(saved.get("fingerprint"))) throw new ActivityConflict("COMMAND_KEY_REUSED");
            return project(courseId, access);
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM grouping_activity WHERE course_id=?)",
                Boolean.class, courseId))) throw new ActivityConflict("ACTIVITY_EXISTS");
        long sum = request.capacities().stream().mapToLong(Integer::longValue).sum();
        if (access.enrolledStudents().size() < 2 || sum != access.enrolledStudents().size())
            throw new ActivityValidationException("capacities");
        // PostgreSQL stores microseconds. Reject instants which would round back to an expired deadline.
        Instant deadline = request.deadline();
        var now = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
        if (!deadline.isAfter(now) || !deadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS).isAfter(now))
            throw new ActivityValidationException("deadline");
        jdbc.update("INSERT INTO grouping_activity(id,course_id,instructions,deadline) VALUES (?,?,?,?)",
                activityId, courseId, request.instructions(), Timestamp.from(deadline));
        for (UUID student : access.enrolledStudents()) jdbc.update(
                "INSERT INTO activity_participant(activity_id,account_id) VALUES (?,?)", activityId, student);
        for (int i = 0; i < request.capacities().size(); i++) jdbc.update(
                "INSERT INTO activity_group(id,activity_id,ordinal,capacity) VALUES (?,?,?,?)",
                UUID.randomUUID(), activityId, i + 1, request.capacities().get(i));
        return project(courseId, access);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Workspace updateInstructions(Principal principal, UUID courseId, UUID commandKey, String instructions) {
        var activity = lockActivity(courseId);
        var access = assignedTeacher(principal, courseId);
        requireKey(commandKey);
        validateInstructions(instructions);
        if (replayed(access, activity.id(), commandKey, "UPDATE_INSTRUCTIONS", fingerprintValues(courseId.toString(), instructions)))
            return project(courseId, access);
        jdbc.update("UPDATE grouping_activity SET instructions=? WHERE id=?", instructions, activity.id());
        return project(courseId, access);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Workspace extendDeadline(Principal principal, UUID courseId, UUID commandKey, Instant deadline) {
        var activity = lockActivity(courseId);
        var access = assignedTeacher(principal, courseId);
        requireKey(commandKey);
        validateDeadline(deadline);
        if (replayed(access, activity.id(), commandKey, "EXTEND_DEADLINE", fingerprintValues(courseId.toString(), deadline.toString())))
            return project(courseId, access);
        // Normalize to the same microsecond precision used for the authoritative comparison and stored value.
        Instant persisted = deadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var now = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
        if (!now.isBefore(activity.deadline())) throw new ActivityConflict("ACTIVITY_CLOSED");
        if (!persisted.isAfter(activity.deadline())) throw new ActivityConflict("DEADLINE_NOT_LATER");
        jdbc.update("UPDATE grouping_activity SET deadline=? WHERE id=?", Timestamp.from(persisted), activity.id());
        return project(courseId, access);
    }
    private record LockedActivity(UUID id, Instant deadline) {}
    private LockedActivity lockActivity(UUID courseId) {
        var rows = jdbc.query("SELECT id, deadline FROM grouping_activity WHERE course_id=? FOR UPDATE",
                (rs, row) -> new LockedActivity(rs.getObject("id", UUID.class), rs.getTimestamp("deadline").toInstant()), courseId);
        if (rows.isEmpty()) throw new CourseNotAccessible();
        return rows.getFirst();
    }
    private ActivityCourseAccess.Access assignedTeacher(Principal principal, UUID courseId) {
        // READ COMMITTED rechecks persisted identity and assignment after any activity-lock wait.
        var access = courses.read(principal, courseId);
        if (!access.teacher()) throw new CourseNotAccessible();
        return access;
    }
    private static void requireKey(UUID commandKey) {
        if (commandKey == null) throw new ActivityValidationException("commandKey");
    }
    private boolean replayed(ActivityCourseAccess.Access access, UUID activityId, UUID key, String command, String fingerprint) {
        int claimed = jdbc.update("""
                INSERT INTO grouping_command_receipt(actor_id, command, command_key, fingerprint, activity_id)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, access.actorId(), command, key, fingerprint, activityId);
        if (claimed != 0) return false;
        String saved = jdbc.queryForObject("""
                SELECT fingerprint FROM grouping_command_receipt WHERE actor_id=? AND command=? AND command_key=?
                """, String.class, access.actorId(), command, key);
        if (!fingerprint.equals(saved)) throw new ActivityConflict("COMMAND_KEY_REUSED");
        return true;
    }
    private static String fingerprintValues(String... values) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            for (String value : values) {
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8); out.writeInt(encoded.length); out.write(encoded);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint activity command", impossible);
        }
    }
    private static void validate(CreateActivity request) {
        if (request == null) throw new ActivityValidationException("instructions");
        validateInstructions(request.instructions());
        validateDeadline(request.deadline());
        if (request.capacities() == null || request.capacities().isEmpty()
                || request.capacities().stream().anyMatch(c -> c == null || c < 2)) throw new ActivityValidationException("capacities");
    }
    private static void validateInstructions(String text) {
        if (text == null || text.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85 || c == 0xfeff)
                || text.codePointCount(0, text.length()) > 5000)
            throw new ActivityValidationException("instructions");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) throw new ActivityValidationException("instructions");
            if (Character.isHighSurrogate(c) && (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))))
                throw new ActivityValidationException("instructions");
        }
    }
    private static void validateDeadline(Instant deadline) {
        // PostgreSQL timestamp's supported upper bound; this is storage validation, not a product deadline limit.
        if (deadline == null || !deadline.isBefore(Instant.parse("+294277-01-01T00:00:00Z")))
            throw new ActivityValidationException("deadline");
    }
    private static String fingerprint(UUID courseId, CreateActivity request) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            for (String value : List.of(courseId.toString(), request.instructions(), request.deadline().toString())) {
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8); out.writeInt(encoded.length); out.write(encoded);
            }
            out.writeInt(request.capacities().size());
            for (int capacity : request.capacities()) out.writeInt(capacity);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint activity command", impossible);
        }
    }
}
