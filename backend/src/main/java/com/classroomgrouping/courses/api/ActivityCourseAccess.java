package com.classroomgrouping.courses.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Course-owned access and enrollment snapshot; creation joins the caller's transaction. */
public interface ActivityCourseAccess {
    Access read(Principal principal, UUID courseId);
    Access lockForCreation(Principal principal, UUID courseId);
    /** Trusted server-side eligibility projection; callers must authorize their acting user separately. */
    boolean isEnrolledStudent(UUID courseId, UUID accountId);
    record Access(UUID actorId, boolean teacher, List<UUID> enrolledStudents) {}
}
