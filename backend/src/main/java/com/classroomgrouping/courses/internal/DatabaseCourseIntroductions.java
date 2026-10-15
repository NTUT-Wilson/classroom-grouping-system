package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.CourseIntroductions;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DatabaseCourseIntroductions implements CourseIntroductions {
    private final NamedParameterJdbcTemplate jdbc;
    DatabaseCourseIntroductions(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<UUID, String> lookup(UUID courseId, Collection<UUID> authorizedParticipantIds) {
        if (authorizedParticipantIds.isEmpty()) return Map.of();
        var result = new LinkedHashMap<UUID, String>();
        authorizedParticipantIds.forEach(id -> result.put(id, ""));
        jdbc.query("SELECT account_id, about_me FROM course_introduction WHERE course_id=:course AND account_id IN (:ids)",
                Map.of("course", courseId, "ids", authorizedParticipantIds), rs -> {
                    result.put(rs.getObject("account_id", UUID.class), rs.getString("about_me"));
                });
        return Map.copyOf(result);
    }
}
