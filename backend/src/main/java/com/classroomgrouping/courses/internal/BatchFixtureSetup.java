package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Additive synthetic fixtures, isolated from the original demo roster and activity history. */
@Configuration
class BatchFixtureSetup {
    @Bean
    ApplicationRunner seedBatchFixture(RosterProvisioning provisioning,
            @Value("${app.batch-fixture.enabled:${APP_BATCH_FIXTURE_ENABLED:false}}") boolean enabled,
            @Value("${BATCH_FIXTURE_PASSWORD:}") String password) {
        return args -> {
            if (!enabled) return;
            // The shared provisioning API validates every identity and password before any writes.
            var accounts = new ArrayList<SeedAccount>();
            accounts.add(new SeedAccount("batch-teacher", password, "TEACHER", null, null));
            var students = new ArrayList<String>();
            var names = List.of("陳 Batch 一", "Alex <Batch> & 二", "Sam Batch 三", "Riley Batch 四",
                    "Morgan Batch 五", "Taylor Batch 六", "Jordan Batch 七", "Casey Batch 八");
            for (int i = 1; i <= 8; i++) {
                var username = "batch-student-" + i;
                students.add(username);
                accounts.add(new SeedAccount(username, password, "STUDENT", "0000700" + i, names.get(i - 1)));
            }
            // V2 uses separate English identities; legacy Unicode fixtures remain unchanged.
            accounts.add(new SeedAccount("v2-teacher", password, "TEACHER", null, null));
            accounts.add(new SeedAccount("v2-co-teacher", password, "TEACHER", null, null));
            accounts.add(new SeedAccount("v2-outsider-teacher", password, "TEACHER", null, null));
            var v2Students = new ArrayList<String>();
            var v2Names = List.of("Alex Fixture", "Blair Fixture", "Casey Fixture", "Devon Fixture",
                    "Ellis Fixture", "Frankie Fixture", "Gray Fixture", "Harper Fixture");
            for (int i = 1; i <= 8; i++) {
                var username = "v2-student-" + i;
                v2Students.add(username);
                accounts.add(new SeedAccount(username, password, "STUDENT", "0000800" + i, v2Names.get(i - 1)));
            }
            accounts.add(new SeedAccount("v2-outsider-student", password, "STUDENT", "00008009", "Indigo Outsider"));
            accounts.add(new SeedAccount("v2-late-student", password, "STUDENT", "00008010", "Jamie Late"));
            provisioning.provision(new Roster(accounts, List.of(
                    new SeedCourse("BATCH-101", "Batch Profile Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-202", "Batch Separate Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-303", "Batch Empty Classroom", List.of("batch-teacher"), List.of()),
                    new SeedCourse("BATCH-404", "Batch Settings Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-405", "Batch Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-505", "Batch Grouping Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-506", "Batch Grouping Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-606", "Batch Applications Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-607", "Batch Applications Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 4)),
                    new SeedCourse("BATCH-707", "Batch Invitations Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-1001", "Batch Results Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-1002", "Batch Results Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 4)),
                    new SeedCourse("BATCH-708", "Batch Invitations Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 4)),
                    new SeedCourse("V2-101", "V2 Lifecycle Classroom", List.of("v2-teacher", "v2-co-teacher"), v2Students),
                    new SeedCourse("V2-202", "V2 No Activity Classroom", List.of("v2-teacher", "v2-co-teacher"), v2Students),
                    new SeedCourse("V2-404", "V2 Cutoff Classroom", List.of("v2-teacher", "v2-co-teacher"), v2Students),
                    new SeedCourse("V2-303", "V2 Other Classroom", List.of("v2-outsider-teacher"), List.of("v2-outsider-student")))));
        };
    }
}
