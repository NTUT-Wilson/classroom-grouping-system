package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.RosterProvisioning;
import com.classroomgrouping.identity.api.AccountProvisioning;
import com.classroomgrouping.identity.api.ProvisioningConflict;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseRosterProvisioning implements RosterProvisioning {
    private final AccountProvisioning accounts;
    private final JdbcTemplate jdbc;
    DatabaseRosterProvisioning(AccountProvisioning accounts, JdbcTemplate jdbc) { this.accounts = accounts; this.jdbc = jdbc; }
    @Transactional
    public void provision(Roster roster) {
        var roles = new HashMap<String, String>();
        for (var account : roster.accounts()) {
            if (roles.put(account.username(), account.role()) != null) throw new ProvisioningConflict();
        }
        var codes = new HashSet<String>();
        for (var course : roster.courses()) {
            if (course.code() == null || course.code().isBlank() || course.code().length() > 100
                    || course.name() == null || course.name().isBlank() || !codes.add(course.code()))
                throw new IllegalArgumentException("Invalid roster course; no changes were committed");
            if (new HashSet<>(course.teachers()).size() != course.teachers().size()
                    || new HashSet<>(course.students()).size() != course.students().size()) throw new ProvisioningConflict();
            for (var teacher : course.teachers()) if (!"TEACHER".equals(roles.get(teacher))) throw new ProvisioningConflict();
            for (var student : course.students()) if (!"STUDENT".equals(roles.get(student))) throw new ProvisioningConflict();
        }
        // Account validation precedes all account writes; its transaction joins this atomic roster unit.
        var ids = accounts.provision(roster.accounts());
        // Stable order and row locks are shared with future activity creation's enrollment snapshot.
        for (var course : roster.courses().stream().sorted(Comparator.comparing(SeedCourse::code)).toList()) {
            var rows = jdbc.queryForList("SELECT id, name FROM course WHERE code = ? FOR UPDATE", course.code());
            UUID id;
            if (rows.isEmpty()) {
                id = UUID.randomUUID();
                jdbc.update("INSERT INTO course(id, code, name) VALUES (?, ?, ?)", id, course.code(), course.name());
                jdbc.queryForList("SELECT id FROM course WHERE id = ? FOR UPDATE", id);
            } else {
                id = (UUID) rows.getFirst().get("id");
                if (!course.name().equals(rows.getFirst().get("name"))) throw new ProvisioningConflict();
            }
            for (var teacher : course.teachers()) jdbc.update(
                    "INSERT INTO course_teacher_assignment(course_id, account_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, ids.get(teacher));
            for (var student : course.students()) jdbc.update(
                    "INSERT INTO course_enrollment(course_id, account_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, ids.get(student));
        }
    }
}
