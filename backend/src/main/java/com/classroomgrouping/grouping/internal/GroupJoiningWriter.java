package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.grouping.api.GroupingConflict;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Called only within the caller's transaction after locking and authorizing the activity. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
class GroupJoiningWriter {
    private final JdbcTemplate jdbc;
    GroupJoiningWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    void join(UUID activityId, Instant deadline, UUID groupId, UUID studentId, UUID acceptedApplication) {
        if (!jdbc.queryForList("SELECT 1 FROM activity_membership WHERE activity_id=? AND account_id=?",activityId,studentId).isEmpty())
            throw new GroupingConflict("ALREADY_GROUPED");
        int capacity = jdbc.queryForObject("SELECT capacity FROM activity_group WHERE activity_id=? AND id=?",Integer.class,activityId,groupId);
        int count = jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=? AND group_id=?",Integer.class,activityId,groupId);
        if (count >= capacity) throw new GroupingConflict("GROUP_FULL");
        requireOpen(deadline);
        jdbc.update("INSERT INTO activity_membership(activity_id,account_id,group_id) VALUES (?,?,?)",activityId,studentId,groupId);
        if (count == 0) jdbc.update("UPDATE activity_group SET manager_account_id=? WHERE activity_id=? AND id=?",studentId,activityId,groupId);
        if (acceptedApplication != null && jdbc.update("UPDATE application_attempt SET status='ACCEPTED' WHERE activity_id=? AND group_id=? AND applicant_account_id=? AND id=? AND status='PENDING'",activityId,groupId,studentId,acceptedApplication) != 1)
            throw new GroupingConflict("APPLICATION_NOT_PENDING");
        jdbc.update("UPDATE application_attempt SET status='CANCELED',reason='Joined a group' WHERE activity_id=? AND applicant_account_id=? AND status='PENDING'",activityId,studentId);
        if (count + 1 == capacity) jdbc.update("UPDATE application_attempt SET status='CANCELED',reason='Group full' WHERE activity_id=? AND group_id=? AND status='PENDING'",activityId,groupId);
    }
    void requireOpen(Instant deadline) {
        if (!jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant().isBefore(deadline))
            throw new GroupingConflict("ACTIVITY_CLOSED");
    }
}
