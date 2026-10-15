package com.classroomgrouping.grouping.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public interface GroupingMemberships {
    Discovery discover(Principal principal, UUID courseId);
    Discovery joinEmpty(Principal principal, UUID courseId, UUID groupId, UUID commandKey);
    record Discovery(UUID activityId, String status, UUID ownGroupId, List<Group> groups) {}
    record Group(UUID id, String name, int capacity, int availablePlaces, Profile manager, List<Profile> members) {}
    record Profile(UUID accountId, String studentId, String studentName, String aboutMe) {}
}
