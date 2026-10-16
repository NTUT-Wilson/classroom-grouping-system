package com.classroomgrouping.grouping.api;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GroupingApplications {
    ApplicationPage history(Principal principal, UUID courseId, String cursor, Integer limit);
    ApplicationPage apply(Principal principal, UUID courseId, UUID groupId, UUID commandKey);
    ApplicationPage decide(Principal principal, UUID courseId, UUID attemptId, UUID commandKey, String decision);
    record ApplicationPage(UUID activityId, String status, List<ApplicationAttempt> items, String nextCursor) {}
    record ApplicationAttempt(UUID id, UUID groupId, String groupName, GroupingMemberships.Profile applicant,
            GroupingMemberships.Profile manager, String status, String reason, Instant createdAt) {}
}
