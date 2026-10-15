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
            provisioning.provision(new Roster(accounts, List.of(
                    new SeedCourse("BATCH-101", "Batch Profile Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-202", "Batch Separate Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-303", "Batch Empty Classroom", List.of("batch-teacher"), List.of()),
                    new SeedCourse("BATCH-404", "Batch Settings Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-405", "Batch Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-505", "Batch Grouping Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-506", "Batch Grouping Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 2)),
                    new SeedCourse("BATCH-606", "Batch Applications Classroom", List.of("batch-teacher"), students),
                    new SeedCourse("BATCH-607", "Batch Applications Cutoff Classroom", List.of("batch-teacher"), students.subList(0, 4)))));
        };
    }
}
