package com.classroomgrouping.courses.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class CourseAnnouncementsController {
    private final CourseAnnouncements announcements;
    public CourseAnnouncementsController(CourseAnnouncements announcements) { this.announcements = announcements; }
    @GetMapping("/api/courses/{courseId}/announcements")
    public CourseAnnouncements.Page list(Principal principal, @PathVariable UUID courseId,
            @RequestParam(required=false) String limit, @RequestParam(required=false) String cursor, HttpServletResponse response) {
        noStore(response); Integer size = null;
        if (limit != null) try { if (!limit.matches("[0-9]{1,2}")) throw new NumberFormatException(); size = Integer.valueOf(limit); }
        catch (NumberFormatException bad) { throw new AnnouncementValidationException("limit"); }
        return announcements.list(principal, courseId, size, cursor);
    }
    @GetMapping("/api/courses/{courseId}/announcements/{id}")
    public CourseAnnouncements.Detail detail(Principal principal, @PathVariable UUID courseId, @PathVariable UUID id, HttpServletResponse response) {
        noStore(response); return announcements.detail(principal, courseId, id);
    }
    @PostMapping("/api/courses/{courseId}/announcements")
    public CourseAnnouncements.Acknowledgement create(Principal principal, @PathVariable UUID courseId, @RequestBody Creation request, HttpServletResponse response) {
        noStore(response); return announcements.create(principal, courseId, key(request.commandKey()), text(request.title()), text(request.body()));
    }
    @PutMapping("/api/courses/{courseId}/announcements/{id}")
    public CourseAnnouncements.Acknowledgement edit(Principal principal, @PathVariable UUID courseId, @PathVariable UUID id, @RequestBody Edit request, HttpServletResponse response) {
        noStore(response); return announcements.edit(principal, courseId, id, key(request.commandKey()), revision(request.expectedRevision()), text(request.title()), text(request.body()));
    }
    @PostMapping("/api/courses/{courseId}/announcements/{id}/delete")
    public CourseAnnouncements.Acknowledgement delete(Principal principal, @PathVariable UUID courseId, @PathVariable UUID id, @RequestBody Deletion request, HttpServletResponse response) {
        noStore(response); return announcements.delete(principal, courseId, id, key(request.commandKey()), revision(request.expectedRevision()));
    }
    @GetMapping("/api/courses/{courseId}/announcement-commands/{commandKey}")
    public CourseAnnouncements.Status status(Principal principal, @PathVariable UUID courseId, @PathVariable UUID commandKey,
            @RequestParam(required=false) String command, HttpServletResponse response) {
        noStore(response); CourseAnnouncements.Command kind;
        try { kind = CourseAnnouncements.Command.valueOf(command == null ? "" : command); }
        catch (IllegalArgumentException invalid) { throw new AnnouncementValidationException("command"); }
        return announcements.status(principal, courseId, commandKey, kind);
    }
    private static void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "private, no-store"); }
    private static String text(JsonNode value) { return value != null && value.isString() ? value.asString() : null; }
    private static UUID key(JsonNode node) {
        try { String value = text(node); UUID id = UUID.fromString(value == null ? "" : value); if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return id; }
        catch (IllegalArgumentException invalid) { throw new AnnouncementValidationException("commandKey"); }
    }
    private static Long revision(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 1) throw new AnnouncementValidationException("expectedRevision");
        return node.longValue();
    }
    public record Creation(JsonNode commandKey, JsonNode title, JsonNode body) {
        @JsonAnySetter public void unknown(String name, JsonNode value) { throw new AnnouncementValidationException("fields"); }
        @Override public String toString() { return "Creation[redacted]"; }
    }
    public record Edit(JsonNode commandKey, JsonNode expectedRevision, JsonNode title, JsonNode body) {
        @JsonAnySetter public void unknown(String name, JsonNode value) { throw new AnnouncementValidationException("fields"); }
        @Override public String toString() { return "Edit[redacted]"; }
    }
    public record Deletion(JsonNode commandKey, JsonNode expectedRevision) {
        @JsonAnySetter public void unknown(String name, JsonNode value) { throw new AnnouncementValidationException("fields"); }
        @Override public String toString() { return "Deletion[redacted]"; }
    }
}
