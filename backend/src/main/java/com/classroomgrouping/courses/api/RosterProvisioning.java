package com.classroomgrouping.courses.api;

import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import java.util.List;

/** Atomic server-only setup; lists describe required relationships, never removals or resets. */
public interface RosterProvisioning {
    void provision(Roster roster);
    record Roster(List<SeedAccount> accounts, List<SeedCourse> courses) {
        @Override public String toString() { return "Roster[redacted]"; }
    }
    record SeedCourse(String code, String name, List<String> teachers, List<String> students) {}
}
