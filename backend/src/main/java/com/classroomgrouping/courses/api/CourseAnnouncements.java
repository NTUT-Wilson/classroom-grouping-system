package com.classroomgrouping.courses.api;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CourseAnnouncements {
    enum Command { CREATE, EDIT, DELETE }
    record Detail(UUID id, UUID courseId, String title, String body, Instant publishedAt, Instant updatedAt, long revision) {
        @Override public String toString() { return "AnnouncementDetail[id=" + id + ", revision=" + revision + ", content=redacted]"; }
    }
    record Page(List<Detail> items, String nextCursor) {}
    record Acknowledgement(UUID id, long revision, boolean deleted) {}
    record Status(boolean committed, Acknowledgement acknowledgement) {}
    Page list(Principal principal, UUID courseId, Integer limit, String cursor);
    Detail detail(Principal principal, UUID courseId, UUID id);
    Acknowledgement create(Principal principal, UUID courseId, UUID key, String title, String body);
    Acknowledgement edit(Principal principal, UUID courseId, UUID id, UUID key, Long revision, String title, String body);
    Acknowledgement delete(Principal principal, UUID courseId, UUID id, UUID key, Long revision);
    Status status(Principal principal, UUID courseId, UUID key, Command command);
}
