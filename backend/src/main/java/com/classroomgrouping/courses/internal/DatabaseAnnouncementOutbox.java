package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.AnnouncementOutbox;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
class DatabaseAnnouncementOutbox implements AnnouncementOutbox {
    private final JdbcTemplate jdbc;
    DatabaseAnnouncementOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(readOnly = true)
    public List<Event> pending(int limit) {
        bound(limit);
        return jdbc.query("SELECT * FROM course_announcement_outbox WHERE acknowledged_at IS NULL ORDER BY created_at,event_id LIMIT ?",
                (rs, row) -> new Event(rs.getObject("event_id", UUID.class), rs.getObject("course_id", UUID.class), rs.getObject("source_id", UUID.class), rs.getLong("revision"), rs.getString("change_kind"), rs.getTimestamp("created_at").toInstant()), limit);
    }
    public boolean acknowledge(UUID event, long revision) {
        var courses = jdbc.query("SELECT course_id FROM course_announcement_outbox WHERE event_id=?", (rs,row) -> rs.getObject(1, UUID.class), event);
        if (courses.isEmpty()) return false;
        lock(courses.getFirst());
        return jdbc.update("UPDATE course_announcement_outbox SET acknowledged_at=clock_timestamp() WHERE event_id=? AND revision=? AND acknowledged_at IS NULL", event, revision) == 1;
    }
    private void lock(UUID course) { jdbc.query("SELECT id FROM course WHERE id=? FOR UPDATE", (rs,row) -> rs.getObject(1, UUID.class), course); }
    private static void bound(int limit) { if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Limit must be between 1 and 1000"); }
}
