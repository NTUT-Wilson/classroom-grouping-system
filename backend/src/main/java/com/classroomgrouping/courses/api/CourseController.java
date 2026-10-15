package com.classroomgrouping.courses.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {
    private final CourseWorkspace workspace;
    public CourseController(CourseWorkspace workspace) { this.workspace = workspace; }
    @GetMapping("/api/courses")
    public List<CourseWorkspace.Course> list(Principal principal) { return workspace.list(principal); }
    @GetMapping("/api/courses/{courseId}")
    public CourseWorkspace.Course detail(Principal principal, @PathVariable UUID courseId) {
        return workspace.detail(principal, courseId);
    }
}
