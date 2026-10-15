package com.classroomgrouping.grouping.api;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GroupingActivities {
    Workspace workspace(Principal principal, UUID courseId);
    Workspace create(Principal principal, UUID courseId, UUID commandKey, CreateActivity request);
    record CreateActivity(String instructions, Instant deadline, List<Integer> capacities) {
        @Override public String toString() { return "CreateActivity[redacted]"; }
    }
    record Workspace(Activity activity, Integer enrolledCount) {}
    record Activity(UUID id, UUID courseId, String instructions, Instant deadline, String status, List<Group> groups) {}
    record Group(UUID id, int ordinal, String name, int capacity) {}
}
