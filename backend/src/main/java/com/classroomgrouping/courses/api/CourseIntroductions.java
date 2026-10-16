package com.classroomgrouping.courses.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Server-only projection. Trusted domain callers must authorize the course and every fixed participant
 * before calling; this deliberately does not filter by mutable current enrollment. */
public interface CourseIntroductions {
    Map<UUID, String> lookup(UUID courseId, Collection<UUID> authorizedParticipantIds);
}
