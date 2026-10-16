package com.classroomgrouping.grouping.api;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GroupingInvitations {
    InvitationPage history(Principal principal, UUID courseId, String cursor, Integer limit);
    InvitationPage invite(Principal principal, UUID courseId, UUID groupId, UUID recipientId, UUID commandKey);
    InvitationPage decide(Principal principal, UUID courseId, UUID attemptId, UUID commandKey, String decision);
    record InvitationPage(UUID activityId, String status, List<InvitationAttempt> items, String nextCursor) {}
    record InvitationAttempt(UUID id, UUID groupId, String groupName, GroupingMemberships.Profile sender,
            GroupingMemberships.Profile recipient, String status, String reason, Instant createdAt) {}
}
