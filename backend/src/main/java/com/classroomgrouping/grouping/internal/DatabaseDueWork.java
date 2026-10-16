package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.grouping.api.DueWork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Immutable preparation and atomic assignment/finalization commit in separate transactions. */
final class DatabaseDueWork implements DueWork {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseDueWork.class);
    private static final int BATCH_SIZE = 64;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ReentrantLock worker = new ReentrantLock();
    private final PreparationObserver observer;

    DatabaseDueWork(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this(jdbc, transactions, new PreparationObserver() {});
    }

    // Package-local failure seam used by database tests; never exposed as an operational control.
    DatabaseDueWork(JdbcTemplate jdbc, PlatformTransactionManager transactions, PreparationObserver observer) {
        this.jdbc = jdbc;
        this.observer = observer;
        transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(30);
    }

    @Override public void processDueWork() {
        if (!worker.tryLock()) return;
        try {
            var candidates = jdbc.queryForList("""
                    SELECT a.id FROM grouping_activity a LEFT JOIN allocation_work w ON w.activity_id=a.id
                    WHERE a.deadline <= clock_timestamp() AND
                      a.finalized_at IS NULL AND
                      (w.activity_id IS NULL OR w.state IN ('PREPARED','COMPLETE') OR (w.state='RETRY' AND w.retry_at <= clock_timestamp()))
                    ORDER BY a.deadline, a.id LIMIT ?
                    """, UUID.class, BATCH_SIZE);
            for (var activity : candidates) {
                try {
                    boolean prepared = Boolean.TRUE.equals(transaction.execute(status -> prepare(activity)));
                    if (prepared) {
                        observer.afterCommit(activity);
                        boolean completed = Boolean.TRUE.equals(transaction.execute(status -> assign(activity)));
                        if (completed) observer.afterAssignmentCommit(activity);
                    }
                } catch (RuntimeException failure) {
                    try {
                        var recorded = transaction.execute(status -> recordFailure(activity, failure));
                        if (recorded != null) LOG.warn("Deadline allocation failure recorded: classification={}, attempts={}",
                                recorded.classification(), recorded.attempts());
                    } catch (RuntimeException bookkeepingFailure) {
                        // Durable deadlines remain discoverable. Do not log SQL, requests, or student data.
                        LOG.warn("Deadline allocation failure bookkeeping unavailable; durable work remains pending");
                    }
                }
            }
        } finally { worker.unlock(); }
    }

    private boolean lockDue(UUID activity) {
        var found = jdbc.queryForList("SELECT id FROM grouping_activity WHERE id=? FOR UPDATE", UUID.class, activity);
        return !found.isEmpty() && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT deadline <= clock_timestamp() FROM grouping_activity WHERE id=?", Boolean.class, activity));
    }

    private boolean eligible(UUID activity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS (SELECT 1 FROM allocation_work WHERE activity_id=? AND
                    (state='HALTED' OR retry_at > clock_timestamp()))
                """, Boolean.class, activity));
    }

    private boolean prepare(UUID activity) {
        if (!lockDue(activity) || !eligible(activity)) return false;
        if (isFinalized(activity)) { validateCompleted(activity); markComplete(activity); return false; }
        require(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM allocation_work WHERE activity_id=? AND state='COMPLETE')", Boolean.class, activity)));
        if (assignmentStage(activity)) {
            require(hasPlan(activity));
            validatePlan(activity, validateSnapshot(activity));
            return true;
        }
        jdbc.update("UPDATE application_attempt SET status='EXPIRED' WHERE activity_id=? AND status='PENDING'", activity);
        jdbc.update("UPDATE invitation_attempt SET status='EXPIRED' WHERE activity_id=? AND status='PENDING'", activity);
        var snapshot = validateSnapshot(activity);
        if (hasPlan(activity)) validatePlan(activity, snapshot);
        else {
            var remaining = new ArrayList<>(snapshot.participants());
            remaining.removeAll(snapshot.memberships().keySet());
            Collections.shuffle(remaining);
            var slots = new ArrayList<UUID>();
            for (var group : snapshot.groups()) {
                int used = Collections.frequency(new ArrayList<>(snapshot.memberships().values()), group.id());
                for (int i = used; i < group.capacity(); i++) slots.add(group.id());
            }
            require(slots.size() == remaining.size());
            jdbc.update("INSERT INTO allocation_plan(activity_id,state,placement_count) VALUES (?,'PREPARING',?)", activity, remaining.size());
            for (int i = 0; i < remaining.size(); i++) {
                jdbc.update("INSERT INTO allocation_placement(activity_id,account_id,group_id) VALUES (?,?,?)",
                        activity, remaining.get(i), slots.get(i));
            }
            jdbc.update("UPDATE allocation_plan SET state='COMPLETE' WHERE activity_id=?", activity);
            validatePlan(activity, snapshot);
        }
        markPrepared(activity);
        observer.beforeCommit(activity);
        return true;
    }

    private boolean assignmentStage(UUID activity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM allocation_work WHERE activity_id=? AND stage='ASSIGNMENT')", Boolean.class, activity));
    }

    private boolean isFinalized(UUID activity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT finalized_at IS NOT NULL FROM grouping_activity WHERE id=?", Boolean.class, activity));
    }

    private boolean assign(UUID activity) {
        if (!lockDue(activity) || !eligible(activity)) return false;
        require(assignmentStage(activity) && hasPlan(activity));
        var snapshot = validateSnapshot(activity);
        validatePlan(activity, snapshot);
        if (isFinalized(activity)) { validateCompleted(activity); markComplete(activity); return false; }
        var placements = placements(activity);
        for (var placement : placements.entrySet()) {
            if (!snapshot.memberships().containsKey(placement.getKey())) {
                jdbc.update("INSERT INTO activity_membership(activity_id,account_id,group_id) VALUES (?,?,?)",
                        activity, placement.getKey(), placement.getValue());
                observer.afterAssignment(activity);
            }
        }
        validateCompleted(activity);
        jdbc.update("UPDATE grouping_activity SET finalized_at=clock_timestamp() WHERE id=?", activity);
        markComplete(activity);
        observer.beforeAssignmentCommit(activity);
        return true;
    }

    private void validateCompleted(UUID activity) {
        require(hasPlan(activity));
        var snapshot = validateSnapshot(activity);
        validatePlan(activity, snapshot);
        require(snapshot.memberships().keySet().equals(snapshot.participants()));
        require(jdbc.queryForObject("SELECT count(*) FROM application_attempt WHERE activity_id=? AND status='PENDING'", Integer.class, activity) == 0);
        require(jdbc.queryForObject("SELECT count(*) FROM invitation_attempt WHERE activity_id=? AND status='PENDING'", Integer.class, activity) == 0);
    }

    private Map<UUID, UUID> placements(UUID activity) {
        var placements = new HashMap<UUID, UUID>();
        jdbc.query("SELECT account_id,group_id FROM allocation_placement WHERE activity_id=?", rs -> {
            require(placements.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) == null);
        }, activity);
        return placements;
    }

    private void markComplete(UUID activity) {
        jdbc.update("UPDATE allocation_work SET state='COMPLETE',stage='ASSIGNMENT',retry_at=NULL,failure_class=NULL WHERE activity_id=?", activity);
    }

    private boolean hasPlan(UUID activity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM allocation_plan WHERE activity_id=?)", Boolean.class, activity));
    }

    private Snapshot validateSnapshot(UUID activity) {
        var participants = new HashSet<>(jdbc.queryForList("SELECT account_id FROM activity_participant WHERE activity_id=?", UUID.class, activity));
        var groups = jdbc.query("SELECT id,capacity,manager_account_id FROM activity_group WHERE activity_id=? ORDER BY ordinal",
                (rs, row) -> new Group(rs.getObject(1, UUID.class), rs.getInt(2), rs.getObject(3, UUID.class)), activity);
        var memberships = new HashMap<UUID, UUID>();
        jdbc.query("SELECT account_id,group_id FROM activity_membership WHERE activity_id=?", rs -> {
            require(memberships.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) == null);
        }, activity);
        require(!groups.isEmpty() && groups.stream().mapToLong(Group::capacity).sum() == participants.size());
        var groupIds = groups.stream().map(Group::id).collect(java.util.stream.Collectors.toSet());
        require(participants.containsAll(memberships.keySet()) && groupIds.containsAll(memberships.values()));
        var planned = hasPlan(activity) ? placements(activity) : Map.<UUID, UUID>of();
        for (var group : groups) {
            long used = memberships.values().stream().filter(group.id()::equals).count();
            require(used <= group.capacity());
            if (group.manager() == null) {
                require(memberships.entrySet().stream().filter(e -> group.id().equals(e.getValue()))
                        .allMatch(e -> group.id().equals(planned.get(e.getKey()))));
            } else {
                require(group.id().equals(memberships.get(group.manager())) && !planned.containsKey(group.manager()));
            }
        }
        return new Snapshot(participants, groups, memberships);
    }

    private void validatePlan(UUID activity, Snapshot snapshot) {
        var header = jdbc.queryForMap("SELECT state,placement_count FROM allocation_plan WHERE activity_id=?", activity);
        require("COMPLETE".equals(header.get("state")));
        var placements = placements(activity);
        require(((Number) header.get("placement_count")).intValue() == placements.size());
        require(snapshot.participants().containsAll(placements.keySet()));
        var combined = new HashMap<>(snapshot.memberships());
        for (var placement : placements.entrySet()) {
            UUID actual = combined.putIfAbsent(placement.getKey(), placement.getValue());
            require(actual == null || actual.equals(placement.getValue()));
        }
        require(combined.keySet().equals(snapshot.participants()));
        var groupIds = snapshot.groups().stream().map(Group::id).collect(java.util.stream.Collectors.toSet());
        require(groupIds.containsAll(combined.values()));
        for (var group : snapshot.groups()) {
            require(combined.values().stream().filter(group.id()::equals).count() == group.capacity());
        }
    }

    private void markPrepared(UUID activity) {
        jdbc.update("""
                INSERT INTO allocation_work(activity_id,state,stage) VALUES (?,'PREPARED','ASSIGNMENT')
                ON CONFLICT(activity_id) DO UPDATE SET state='PREPARED',stage='ASSIGNMENT',retry_at=NULL,failure_class=NULL
                """, activity);
    }

    private FailureNotice recordFailure(UUID activity, RuntimeException failure) {
        if (!lockDue(activity)) return null;
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM allocation_work WHERE activity_id=? AND state='HALTED')", Boolean.class, activity))) return null;
        // Reconcile an uncertain final commit only against complete authoritative state.
        // A saved plan alone must never erase assignment retry backoff.
        try {
            if (isFinalized(activity)) { validateCompleted(activity); markComplete(activity); return null; }
            if (assignmentStage(activity)) {
                require(hasPlan(activity));
                validatePlan(activity, validateSnapshot(activity));
            } else if (hasPlan(activity)) {
                validatePlan(activity, validateSnapshot(activity));
                markPrepared(activity);
                return null;
            }
        } catch (IntegrityFault invalidPlan) { failure = invalidPlan; }
        boolean integrity = failure instanceof IntegrityFault || failure instanceof DataIntegrityViolationException;
        var attempts = jdbc.queryForList("SELECT attempts FROM allocation_work WHERE activity_id=?", Integer.class, activity);
        int count = attempts.isEmpty() ? 1 : (int) Math.min(Integer.MAX_VALUE, (long) attempts.getFirst() + 1);
        long seconds = Math.min(3600, 5L << Math.min(count - 1, 10));
        jdbc.update("""
                INSERT INTO allocation_work(activity_id,state,attempts,retry_at,failure_class)
                VALUES (?,?,?,CASE WHEN ? THEN NULL ELSE clock_timestamp()+make_interval(secs => ?) END,?)
                ON CONFLICT(activity_id) DO UPDATE SET state=excluded.state,attempts=excluded.attempts,
                    retry_at=excluded.retry_at,failure_class=excluded.failure_class
                """, activity, integrity ? "HALTED" : "RETRY", count, integrity, seconds, integrity ? "INTEGRITY" : "TRANSIENT");
        return new FailureNotice(integrity ? "INTEGRITY" : "TRANSIENT", count);
    }

    private static void require(boolean condition) { if (!condition) throw new IntegrityFault(); }
    private static final class IntegrityFault extends RuntimeException {}
    private record Group(UUID id, int capacity, UUID manager) {}
    private record FailureNotice(String classification, int attempts) {}
    private record Snapshot(java.util.Set<UUID> participants, List<Group> groups, Map<UUID, UUID> memberships) {}
    interface PreparationObserver {
        default void beforeCommit(UUID activity) {}
        default void afterCommit(UUID activity) {}
        default void afterAssignment(UUID activity) {}
        default void beforeAssignmentCommit(UUID activity) {}
        default void afterAssignmentCommit(UUID activity) {}
    }
}
