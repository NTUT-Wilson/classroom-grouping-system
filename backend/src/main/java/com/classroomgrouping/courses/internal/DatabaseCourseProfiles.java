package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.CourseProfiles;
import com.classroomgrouping.courses.api.ProfileValidationException;
import com.classroomgrouping.identity.api.SessionActors;
import com.classroomgrouping.identity.api.StudentIdentities;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DatabaseCourseProfiles implements CourseProfiles {
    private final JdbcTemplate jdbc;
    private final SessionActors actors;
    private final StudentIdentities identities;
    DatabaseCourseProfiles(JdbcTemplate jdbc, SessionActors actors, StudentIdentities identities) {
        this.jdbc = jdbc; this.actors = actors; this.identities = identities;
    }
    private UUID requireStudent(Principal principal, UUID courseId) {
        var actor = actors.require(principal);
        if (!"STUDENT".equals(actor.role()) || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM course_enrollment WHERE course_id = ? AND account_id = ?)",
                Boolean.class, courseId, actor.accountId()))) throw new CourseNotAccessible();
        return actor.accountId();
    }
    public List<Profile> classmates(Principal principal, UUID courseId) {
        requireStudent(principal, courseId);
        var introductions = jdbc.query("""
                SELECT e.account_id, COALESCE(i.about_me, '') AS about_me
                FROM course_enrollment e LEFT JOIN course_introduction i
                ON i.course_id = e.course_id AND i.account_id = e.account_id WHERE e.course_id = ?
                """, (rs, row) -> new Introduction(rs.getObject("account_id", UUID.class), rs.getString("about_me")), courseId);
        var students = identities.lookup(introductions.stream().map(Introduction::accountId).toList());
        return introductions.stream().map(i -> {
            var student = Objects.requireNonNull(students.get(i.accountId()), "Enrolled identity is missing");
            return new Profile(student.accountId(), student.studentId(), student.studentName(), i.aboutMe());
        }).sorted(Comparator.comparing(Profile::studentId).thenComparing(Profile::accountId)).toList();
    }
    public Profile own(Principal principal, UUID courseId) {
        var accountId = requireStudent(principal, courseId);
        return ownProfile(courseId, accountId);
    }
    private Profile ownProfile(UUID courseId, UUID accountId) {
        var student = identities.lookup(List.of(accountId)).get(accountId);
        if (student == null) throw new CourseNotAccessible();
        var values = jdbc.query("SELECT about_me FROM course_introduction WHERE course_id = ? AND account_id = ?",
                (rs, row) -> rs.getString("about_me"), courseId, accountId);
        return new Profile(accountId, student.studentId(), student.studentName(), values.isEmpty() ? "" : values.getFirst());
    }
    @Transactional
    public Profile save(Principal principal, UUID courseId, String aboutMe) {
        var accountId = requireStudent(principal, courseId);
        validate(aboutMe);
        jdbc.update("""
                INSERT INTO course_introduction(course_id, account_id, about_me) VALUES (?, ?, ?)
                ON CONFLICT (course_id, account_id) DO UPDATE SET about_me = EXCLUDED.about_me
                """, courseId, accountId, aboutMe);
        return ownProfile(courseId, accountId);
    }
    private static void validate(String text) {
        if (text == null || text.codePointCount(0, text.length()) > 500) throw new ProfileValidationException();
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == 0 || Character.isLowSurrogate(value)) throw new ProfileValidationException();
            if (Character.isHighSurrogate(value)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) throw new ProfileValidationException();
            }
        }
    }
    private record Introduction(UUID accountId, String aboutMe) {}
}
