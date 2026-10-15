package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.courses.api.RosterProvisioning.Roster;
import com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse;
import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate opt-in synthetic course workspace, no activities or mutable history. */
@Configuration
@ConditionalOnProperty(name = "app.roster.enabled", havingValue = "true")
class DemoRosterSetup {
    @Bean
    ApplicationRunner seedDemoRoster(RosterProvisioning provisioning,
            @Value("${app.roster.teacher-password}") String teacherPassword,
            @Value("${app.roster.student-password}") String studentPassword) {
        return args -> provisioning.provision(new Roster(List.of(
                new SeedAccount("roster-teacher", teacherPassword, "TEACHER", null, null),
                new SeedAccount("roster-other-teacher", teacherPassword, "TEACHER", null, null),
                new SeedAccount("roster-student", studentPassword, "STUDENT", "00001234", "陳 Demo"),
                new SeedAccount("roster-other-student", studentPassword, "STUDENT", "00005678", "Alex Example"),
                new SeedAccount("roster-empty-student", studentPassword, "STUDENT", "00009012", "Sam Empty")),
                List.of(new SeedCourse("DEMO-101", "Synthetic Classroom", List.of("roster-teacher"), List.of("roster-student")),
                        new SeedCourse("DEMO-202", "Another Synthetic Classroom", List.of("roster-other-teacher"), List.of("roster-other-student")))));
    }
}
