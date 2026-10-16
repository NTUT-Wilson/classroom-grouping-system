package com.classroomgrouping.identity.internal;

import com.classroomgrouping.identity.api.StudentIdentities;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DatabaseStudentIdentities implements StudentIdentities {
    private final NamedParameterJdbcTemplate jdbc;
    DatabaseStudentIdentities(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<UUID, Student> lookup(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        var result = new LinkedHashMap<UUID, Student>();
        jdbc.query("SELECT account_id, student_id, student_name FROM student_identity WHERE account_id IN (:ids)",
                Map.of("ids", accountIds), rs -> {
                    var id = rs.getObject("account_id", UUID.class);
                    result.put(id, new Student(id, rs.getString("student_id"), rs.getString("student_name")));
                });
        return Map.copyOf(result);
    }
}
