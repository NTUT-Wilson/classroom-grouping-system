package com.classroomgrouping.courses.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Metadata-only owner handoff. Methods join a caller's same-datasource transaction. No content commands. */
public interface AnnouncementOutbox {
    record Event(UUID eventId, UUID courseId, UUID sourceId, long revision, String changeKind, Instant createdAt) {}
    List<Event> pending(int limit);
    boolean acknowledge(UUID eventId, long revision);
}
