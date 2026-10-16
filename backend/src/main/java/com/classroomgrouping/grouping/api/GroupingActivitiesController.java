package com.classroomgrouping.grouping.api;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class GroupingActivitiesController {
    private final GroupingActivities activities;
    public GroupingActivitiesController(GroupingActivities activities) { this.activities = activities; }
    @GetMapping("/api/courses/{courseId}/activity")
    public GroupingActivities.Workspace workspace(Principal principal, @PathVariable UUID courseId) {
        return activities.workspace(principal, courseId);
    }
    @PostMapping("/api/courses/{courseId}/activity")
    public GroupingActivities.Workspace create(Principal principal, @PathVariable UUID courseId, @RequestBody Creation request) {
        UUID key;
        try {
            if (request.commandKey() == null || !request.commandKey().isString()) throw new IllegalArgumentException();
            String value = request.commandKey().asString(); key = UUID.fromString(value);
            if (!key.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new ActivityValidationException("commandKey"); }
        java.time.Instant deadline;
        try {
            if (request.deadline() == null || !request.deadline().isString()) throw new IllegalArgumentException();
            deadline = OffsetDateTime.parse(request.deadline().asString()).toInstant();
        } catch (java.time.DateTimeException | IllegalArgumentException invalid) { throw new ActivityValidationException("deadline"); }
        if (request.capacities() == null || !request.capacities().isArray()) throw new ActivityValidationException("capacities");
        var capacities = new ArrayList<Integer>();
        for (var capacity : request.capacities()) {
            if (!capacity.isIntegralNumber() || !capacity.canConvertToInt()) throw new ActivityValidationException("capacities");
            capacities.add(capacity.intValue());
        }
        return activities.create(principal, courseId, key, new GroupingActivities.CreateActivity(
                request.instructions() != null && request.instructions().isString() ? request.instructions().asString() : null,
                deadline, capacities));
    }
    @PutMapping("/api/courses/{courseId}/activity/instructions")
    public GroupingActivities.Workspace updateInstructions(Principal principal, @PathVariable UUID courseId,
            @RequestBody InstructionsUpdate request) {
        var text = request.instructions();
        return activities.updateInstructions(principal, courseId, key(request.commandKey()),
                text != null && text.isString() ? text.asString() : null);
    }
    @PostMapping("/api/courses/{courseId}/activity/deadline")
    public GroupingActivities.Workspace extendDeadline(Principal principal, @PathVariable UUID courseId,
            @RequestBody DeadlineExtension request) {
        return activities.extendDeadline(principal, courseId, key(request.commandKey()), deadline(request.deadline()));
    }
    private static UUID key(JsonNode node) {
        try {
            if (node == null || !node.isString()) throw new IllegalArgumentException();
            String value = node.asString(); UUID key = UUID.fromString(value);
            if (!key.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException invalid) { throw new ActivityValidationException("commandKey"); }
    }
    private static java.time.Instant deadline(JsonNode node) {
        try {
            if (node == null || !node.isString()) throw new IllegalArgumentException();
            return OffsetDateTime.parse(node.asString()).toInstant();
        } catch (java.time.DateTimeException | IllegalArgumentException invalid) { throw new ActivityValidationException("deadline"); }
    }
    public record InstructionsUpdate(JsonNode commandKey, JsonNode instructions) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) { throw new ActivityValidationException("fields"); }
        @Override public String toString() { return "InstructionsUpdate[redacted]"; }
    }
    public record DeadlineExtension(JsonNode commandKey, JsonNode deadline) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) { throw new ActivityValidationException("fields"); }
        @Override public String toString() { return "DeadlineExtension[redacted]"; }
    }
    public record Creation(JsonNode commandKey, JsonNode instructions, JsonNode deadline, JsonNode capacities) {
        @Override public String toString() { return "Creation[redacted]"; }
    }
}
