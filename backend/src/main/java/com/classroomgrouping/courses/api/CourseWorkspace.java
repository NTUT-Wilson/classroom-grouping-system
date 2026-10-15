package com.classroomgrouping.courses.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public interface CourseWorkspace {
    List<Course> list(Principal principal);
    Course detail(Principal principal, UUID courseId);
    record Course(UUID id, String code, String name) {}
}
