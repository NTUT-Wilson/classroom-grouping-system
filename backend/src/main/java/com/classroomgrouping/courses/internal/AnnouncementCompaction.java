package com.classroomgrouping.courses.internal;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Caller holds the course row UPDATE lock; acknowledgements and source writes use the same order. */
final class AnnouncementCompaction {
    private AnnouncementCompaction() {}
    static int compact(JdbcTemplate jdbc, UUID course, int limit) {
        return jdbc.update("""
            DELETE FROM course_announcement_outbox WHERE event_id IN (
              SELECT old.event_id FROM course_announcement_outbox old
              WHERE old.course_id=? AND EXISTS (
                SELECT 1 FROM course_announcement_outbox newer
                WHERE newer.source_id=old.source_id AND newer.revision>old.revision)
              ORDER BY old.created_at,old.event_id LIMIT ?)
            """, course, limit);
    }
}
