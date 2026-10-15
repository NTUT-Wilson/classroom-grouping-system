package com.classroomgrouping.identity.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only provisioning boundary. Never exposed as a browser mutation. */
public interface AccountProvisioning {
    Map<String, UUID> provision(List<SeedAccount> accounts);
    record SeedAccount(String username, String password, String role, String studentId, String studentName) {
        @Override public String toString() { return "SeedAccount[redacted]"; }
    }
}
