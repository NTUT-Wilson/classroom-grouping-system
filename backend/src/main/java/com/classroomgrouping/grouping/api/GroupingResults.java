package com.classroomgrouping.grouping.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Authorized, complete, read-only final-result snapshots. */
public interface GroupingResults {
    Result roster(Principal principal, UUID courseId);
    Result myGroup(Principal principal, UUID courseId);
    record Result(UUID activityId, String status, List<Group> groups) {
        public Result { groups = List.copyOf(groups); }
    }
    record Group(UUID id, int ordinal, String name, int capacity, UUID managerAccountId, List<Member> members) {
        public Group { members = List.copyOf(members); }
    }
    record Member(UUID accountId, String studentId, String studentName,
            @JsonInclude(JsonInclude.Include.NON_NULL) String aboutMe) {}
}
