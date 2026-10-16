package com.classroomgrouping.grouping.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class GroupingMembershipsController {
    private final GroupingMemberships memberships;
    public GroupingMembershipsController(GroupingMemberships memberships) { this.memberships = memberships; }
    @GetMapping("/api/courses/{courseId}/grouping")
    public GroupingMemberships.Discovery discover(Principal principal, @PathVariable UUID courseId) {
        return memberships.discover(principal, courseId);
    }
    @PostMapping("/api/courses/{courseId}/groups/{groupId}/join")
    public GroupingMemberships.Discovery join(Principal principal, @PathVariable UUID courseId,
            @PathVariable UUID groupId, @RequestBody JoinEmptyGroup request) {
        UUID key;
        try {
            if (request.commandKey() == null || !request.commandKey().isString()) throw new IllegalArgumentException();
            String value = request.commandKey().asString(); key = UUID.fromString(value);
            if (!key.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new GroupingValidationException("commandKey"); }
        return memberships.joinEmpty(principal, courseId, groupId, key);
    }
    public record JoinEmptyGroup(JsonNode commandKey) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) { throw new GroupingValidationException("fields"); }
        @Override public String toString() { return "JoinEmptyGroup[redacted]"; }
    }
}
