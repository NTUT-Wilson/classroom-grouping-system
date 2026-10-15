package com.classroomgrouping.identity.api;

import java.security.Principal;
import java.util.UUID;

/** Current persisted identity, resolved anew for each operation. */
public interface SessionActors {
    Actor require(Principal principal);
    record Actor(UUID accountId, String username, String role) {}
}
