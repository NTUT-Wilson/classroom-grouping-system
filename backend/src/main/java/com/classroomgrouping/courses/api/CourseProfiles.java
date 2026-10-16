package com.classroomgrouping.courses.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public interface CourseProfiles {
    List<Profile> classmates(Principal principal, UUID courseId);
    Profile own(Principal principal, UUID courseId);
    Profile save(Principal principal, UUID courseId, String aboutMe);
    record Profile(UUID accountId, String studentId, String studentName, String aboutMe) {}
}
