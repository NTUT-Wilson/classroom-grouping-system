package com.classroomgrouping.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Identity-owned projection for authorized course rosters. */
public interface StudentIdentities {
    Map<UUID, Student> lookup(Collection<UUID> accountIds);
    record Student(UUID accountId, String studentId, String studentName) {}
}
