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
        return memberships.joinEmpty(principal, courseId, groupId, uuid(request.commandKey(), "commandKey"));
    }
    @PostMapping("/api/courses/{courseId}/groups/{groupId}/leave")
    public GroupingMemberships.Discovery leave(Principal principal, @PathVariable UUID courseId,
            @PathVariable UUID groupId, @RequestBody LeaveGroup request) {
        return memberships.leave(principal, courseId, groupId, uuid(request.commandKey(), "commandKey"));
    }
    @PostMapping("/api/courses/{courseId}/groups/{groupId}/manager-transfer")
    public GroupingMemberships.Discovery transfer(Principal principal, @PathVariable UUID courseId,
            @PathVariable UUID groupId, @RequestBody TransferManager request) {
        return memberships.transferManager(principal, courseId, groupId,
                uuid(request.commandKey(), "commandKey"), uuid(request.targetMemberId(), "targetMemberId"));
    }
    private static UUID uuid(JsonNode node, String field) {
        try {
            if (node == null || !node.isString()) throw new IllegalArgumentException();
            String value = node.asString(); UUID key = UUID.fromString(value);
            if (!key.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException invalid) { throw new GroupingValidationException(field); }
    }
    public record JoinEmptyGroup(JsonNode commandKey) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) { throw new GroupingValidationException("fields"); }
        @Override public String toString() { return "JoinEmptyGroup[redacted]"; }
    }
    public record LeaveGroup(JsonNode commandKey) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) { throw new GroupingValidationException("fields"); }
        @Override public String toString() { return "LeaveGroup[redacted]"; }
    }
    public record TransferManager(JsonNode commandKey, JsonNode targetMemberId) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) { throw new GroupingValidationException("fields"); }
        @Override public String toString() { return "TransferManager[redacted]"; }
    }
}
