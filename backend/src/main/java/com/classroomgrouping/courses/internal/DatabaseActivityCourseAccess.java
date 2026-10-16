package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.ActivityCourseAccess;
import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.identity.api.SessionActors;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseActivityCourseAccess implements ActivityCourseAccess {
    private final JdbcTemplate jdbc;
    private final SessionActors actors;
    DatabaseActivityCourseAccess(JdbcTemplate jdbc, SessionActors actors) { this.jdbc = jdbc; this.actors = actors; }
    @Transactional(readOnly = true)
    public Access read(Principal principal, UUID courseId) { return authorize(principal, courseId, false); }
    @Transactional(readOnly = true)
    public boolean isEnrolledStudent(UUID courseId, UUID accountId) {
        // Enrollment's FK to student_identity and its account-role FK guarantee a current student role.
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM course_enrollment WHERE course_id=? AND account_id=?)",
                Boolean.class, courseId, accountId));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Access lockForCreation(Principal principal, UUID courseId) {
        if (jdbc.queryForList("SELECT id FROM course WHERE id=? FOR UPDATE", courseId).isEmpty())
            throw new CourseNotAccessible();
        return authorize(principal, courseId, true);
    }
    private Access authorize(Principal principal, UUID courseId, boolean creating) {
        var actor = actors.require(principal);
        boolean teacher = "TEACHER".equals(actor.role());
        String relation = teacher ? "course_teacher_assignment" : "course_enrollment";
        if ((!teacher && (creating || !"STUDENT".equals(actor.role()))) || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + relation + " WHERE course_id=? AND account_id=?)",
                Boolean.class, courseId, actor.accountId()))) throw new CourseNotAccessible();
        List<UUID> students = teacher ? jdbc.query("SELECT account_id FROM course_enrollment WHERE course_id=? ORDER BY account_id",
                (rs, row) -> rs.getObject(1, UUID.class), courseId) : List.of();
        return new Access(actor.accountId(), teacher, students);
    }
}
