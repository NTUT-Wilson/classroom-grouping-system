package com.classroomgrouping.grouping.api;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
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
    public record Creation(JsonNode commandKey, JsonNode instructions, JsonNode deadline, JsonNode capacities) {
        @Override public String toString() { return "Creation[redacted]"; }
    }
}
