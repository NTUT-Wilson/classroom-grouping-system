package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.CourseWorkspace;
import com.classroomgrouping.identity.api.SessionActors;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DatabaseCourseWorkspace implements CourseWorkspace {
    private final JdbcTemplate jdbc;
    private final SessionActors actors;
    DatabaseCourseWorkspace(JdbcTemplate jdbc, SessionActors actors) { this.jdbc = jdbc; this.actors = actors; }
    private String relationship(String role) {
        return switch (role) {
            case "TEACHER" -> "course_teacher_assignment";
            case "STUDENT" -> "course_enrollment";
            default -> throw new CourseNotAccessible();
        };
    }
    public List<Course> list(Principal principal) {
        var actor = actors.require(principal);
        return jdbc.query("SELECT c.id, c.code, c.name FROM course c JOIN " + relationship(actor.role())
                + " r ON r.course_id = c.id WHERE r.account_id = ? ORDER BY c.code, c.id",
                (rs, row) -> new Course(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")),
                actor.accountId());
    }
    public Course detail(Principal principal, UUID courseId) {
        var actor = actors.require(principal);
        var courses = jdbc.query("SELECT c.id, c.code, c.name FROM course c JOIN " + relationship(actor.role())
                + " r ON r.course_id = c.id WHERE c.id = ? AND r.account_id = ?",
                (rs, row) -> new Course(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")),
                courseId, actor.accountId());
        if (courses.isEmpty()) throw new CourseNotAccessible();
        return courses.getFirst();
    }
}
