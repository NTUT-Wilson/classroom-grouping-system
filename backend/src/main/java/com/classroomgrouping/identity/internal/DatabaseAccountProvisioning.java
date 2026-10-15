package com.classroomgrouping.identity.internal;

import com.classroomgrouping.identity.api.AccountProvisioning;
import com.classroomgrouping.identity.api.ProvisioningConflict;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseAccountProvisioning implements AccountProvisioning {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    DatabaseAccountProvisioning(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }
    @Transactional
    public Map<String, UUID> provision(List<SeedAccount> accounts) {
        var usernames = new HashSet<String>();
        var studentIds = new HashSet<String>();
        for (var account : accounts) {
            SeedIdentityValidation.validate(account);
            if (!usernames.add(account.username()) || (account.studentId() != null && !studentIds.add(account.studentId())))
                throw new ProvisioningConflict();
        }
        // Shared by all account setup paths; concurrent opt-in startup cannot race unique usernames.
        jdbc.execute("SELECT pg_advisory_xact_lock(73109421)");
        var result = new HashMap<String, UUID>();
        for (var account : accounts) {
            var existing = jdbc.queryForList("SELECT id, role FROM app_account WHERE username = ?", account.username());
            UUID id;
            if (existing.isEmpty()) {
                id = UUID.randomUUID();
                jdbc.update("INSERT INTO app_account(id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                        id, account.username(), encoder.encode(account.password()), account.role());
            } else {
                id = (UUID) existing.getFirst().get("id");
                if (!account.role().equals(existing.getFirst().get("role"))) throw new ProvisioningConflict();
            }
            if ("STUDENT".equals(account.role())) {
                var identities = jdbc.queryForList("SELECT student_id, student_name FROM student_identity WHERE account_id = ?", id);
                if (identities.isEmpty()) {
                    if (!jdbc.queryForList("SELECT account_id FROM student_identity WHERE student_id = ?", account.studentId()).isEmpty())
                        throw new ProvisioningConflict();
                    jdbc.update("INSERT INTO student_identity(account_id, student_id, student_name) VALUES (?, ?, ?)",
                            id, account.studentId(), account.studentName());
                } else if (!account.studentId().equals(identities.getFirst().get("student_id"))
                        || !account.studentName().equals(identities.getFirst().get("student_name"))) throw new ProvisioningConflict();
            }
            result.put(account.username(), id);
        }
        return Map.copyOf(result);
    }
}
