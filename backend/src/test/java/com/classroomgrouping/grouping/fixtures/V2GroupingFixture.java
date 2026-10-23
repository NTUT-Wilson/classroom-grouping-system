package com.classroomgrouping.grouping.fixtures;

import com.classroomgrouping.grouping.api.GroupingActivities;
import com.classroomgrouping.grouping.api.GroupingApplications;
import com.classroomgrouping.grouping.api.GroupingInvitations;
import com.classroomgrouping.grouping.api.GroupingMemberships;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Test-only setup for a fresh disposable V2-101 or V2-404 course. All writes use
 * current public APIs; this is deliberately not a startup runner or an activity reset.
 * Callers choose the deadline (including a near-cutoff instant for race tests).
 */
public final class V2GroupingFixture {
    private V2GroupingFixture() {}

    public static Scenario create(GroupingActivities activities, GroupingMemberships memberships,
            GroupingApplications applications, GroupingInvitations invitations,
            UUID courseId, UUID invitedStudentId, UUID rejectedInviteStudentId, Instant deadline) {
        var workspace = activities.create(() -> "v2-teacher", courseId, UUID.randomUUID(),
                new GroupingActivities.CreateActivity("Synthetic V2 lifecycle exercise", deadline, List.of(4, 4)));
        var soleGroup = workspace.activity().groups().get(0).id();
        var multiGroup = workspace.activity().groups().get(1).id();
        memberships.joinEmpty(() -> "v2-student-1", courseId, soleGroup, UUID.randomUUID());
        memberships.joinEmpty(() -> "v2-student-2", courseId, multiGroup, UUID.randomUUID());
        var accepted = applications.apply(() -> "v2-student-3", courseId, multiGroup, UUID.randomUUID()).items().getFirst().id();
        applications.decide(() -> "v2-student-2", courseId, accepted, UUID.randomUUID(), "APPROVE");
        var rejected = applications.apply(() -> "v2-student-5", courseId, soleGroup, UUID.randomUUID()).items().getFirst().id();
        applications.decide(() -> "v2-student-1", courseId, rejected, UUID.randomUUID(), "REJECT");
        var pending = applications.apply(() -> "v2-student-4", courseId, soleGroup, UUID.randomUUID()).items().getFirst().id();
        var rejectedInvite = invitations.invite(() -> "v2-student-1", courseId, soleGroup,
                rejectedInviteStudentId, UUID.randomUUID()).items().getFirst().id();
        invitations.decide(() -> "v2-student-7", courseId, rejectedInvite, UUID.randomUUID(), "REJECT");
        var pendingInvite = invitations.invite(() -> "v2-student-1", courseId, soleGroup,
                invitedStudentId, UUID.randomUUID()).items().stream()
                .filter(attempt -> attempt.recipient().accountId().equals(invitedStudentId))
                .findFirst().orElseThrow().id();
        return new Scenario(courseId, soleGroup, multiGroup, accepted, rejected, pending, rejectedInvite, pendingInvite);
    }

    public record Scenario(UUID courseId, UUID soleManagerGroupId, UUID multiMemberGroupId,
            UUID acceptedApplicationId, UUID rejectedApplicationId, UUID pendingApplicationId,
            UUID rejectedInvitationId, UUID pendingInvitationId) {}
}
