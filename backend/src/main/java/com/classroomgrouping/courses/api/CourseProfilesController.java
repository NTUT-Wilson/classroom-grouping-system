package com.classroomgrouping.courses.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class CourseProfilesController {
    private final CourseProfiles profiles;
    public CourseProfilesController(CourseProfiles profiles) { this.profiles = profiles; }
    @GetMapping("/api/courses/{courseId}/classmates")
    public List<CourseProfiles.Profile> classmates(Principal principal, @PathVariable UUID courseId) {
        return profiles.classmates(principal, courseId);
    }
    @GetMapping("/api/courses/{courseId}/about-me")
    public CourseProfiles.Profile own(Principal principal, @PathVariable UUID courseId) {
        return profiles.own(principal, courseId);
    }
    @PutMapping("/api/courses/{courseId}/about-me")
    public CourseProfiles.Profile save(Principal principal, @PathVariable UUID courseId,
            @RequestBody UpdateIntroduction request) {
        return profiles.save(principal, courseId,
                request.aboutMe() != null && request.aboutMe().isString() ? request.aboutMe().asString() : null);
    }
    public record UpdateIntroduction(JsonNode aboutMe) {}
}
