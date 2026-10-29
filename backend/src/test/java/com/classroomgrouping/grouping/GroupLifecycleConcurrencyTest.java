package com.classroomgrouping.grouping;

import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.api.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Each ordering is established through independent PostgreSQL connections and observed lock waiters. */
class GroupLifecycleConcurrencyTest extends GroupLifecycleTestSupport {
    private List<Object> ordered(Fixture f, Supplier<?> first, Supplier<?> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(f)) {
            var a = pool.submit(() -> transaction(first)); awaitWaiters(pid(gate), 1);
            var b = pool.submit(() -> transaction(second)); awaitWaiters(pid(gate), 2); gate.commit();
            return List.of(outcome(a), outcome(b));
        }
    }
    @Test void competingMemberAndManagerLeavesSerializeToEmptyAndNoDanglingManager() throws Exception {
        var f = fixture("life-race-leaves"); populate(f, 2);
        applications.apply(() -> f.user(2), f.course(), f.group(), UUID.randomUUID());
        invitations.invite(() -> f.user(0), f.course(), f.group(), f.students().get(3), UUID.randomUUID());
        var outcomes = ordered(f, () -> leave(f, 0, UUID.randomUUID()), () -> leave(f, 1, UUID.randomUUID()));
        outcomes.forEach(o -> assertInstanceOf(GroupingMemberships.Discovery.class, o));
        assertEquals(0, occupancy(f)); assertNull(manager(f)); integrity(f);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM application_attempt WHERE activity_id=? AND reason='Group empty'", Integer.class, f.activity()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM invitation_attempt WHERE activity_id=? AND reason='Group empty'", Integer.class, f.activity()));
    }
    @Test void duplicateLeaveKeyCommitsExactlyOnceAndDoesNotRandomizeSuccessorAgain() throws Exception {
        var f = fixture("life-race-key"); populate(f, 3); var key = UUID.randomUUID();
        var outcomes = ordered(f, () -> leave(f, 0, key), () -> leave(f, 0, key));
        assertEquals(outcomes.getFirst(), outcomes.get(1)); assertEquals(2, occupancy(f)); assertEquals(1, receipts(key)); integrity(f);
        var before = snapshot(f); assertThrows(CourseNotAccessible.class, () -> leave(f, 0, UUID.randomUUID())); assertEquals(before, snapshot(f));
    }
    @Test void soleDepartureAndEmptyJoinObserveCommittedOrderWithoutOvercapacityOrManagerLoss() throws Exception {
        var f = fixture("life-race-return"); populate(f, 1);
        var outcomes = ordered(f, () -> leave(f, 0, UUID.randomUUID()), () -> join(f, 1, UUID.randomUUID()));
        outcomes.forEach(o -> assertInstanceOf(GroupingMemberships.Discovery.class, o));
        assertEquals(1, occupancy(f)); assertEquals(f.students().get(1), manager(f)); integrity(f);
        var opposite = fixture("life-race-join-first"); populate(opposite, 1);
        var reverse = ordered(opposite, () -> join(opposite, 1, UUID.randomUUID()), () -> leave(opposite, 0, UUID.randomUUID()));
        assertEquals("GROUP_NOT_EMPTY", assertInstanceOf(GroupingConflict.class, reverse.getFirst()).code());
        assertInstanceOf(GroupingMemberships.Discovery.class, reverse.get(1)); assertNull(manager(opposite)); integrity(opposite);
    }
    @Test void lastPlaceApprovalBeforeDeparturePreservesFullCancellationWhileDepartureFirstAllowsBoth() throws Exception {
        var f = fixture("life-race-last"); populate(f, 3);
        var loser = applications.apply(() -> f.user(4), f.course(), f.group(), UUID.randomUUID()).items().getFirst().id();
        var attempt = applications.apply(() -> f.user(3), f.course(), f.group(), UUID.randomUUID()).items().getFirst().id();
        var outcomes = ordered(f, () -> applications.decide(() -> f.user(0), f.course(), attempt, UUID.randomUUID(), "APPROVE"),
                () -> leave(f, 1, UUID.randomUUID()));
        assertInstanceOf(GroupingApplications.ApplicationPage.class, outcomes.getFirst());
        assertInstanceOf(GroupingMemberships.Discovery.class, outcomes.get(1)); assertEquals(3, occupancy(f));
        assertEquals("Group full", jdbc.queryForObject("SELECT reason FROM application_attempt WHERE id=?", String.class, loser)); integrity(f);
        var g = fixture("life-race-vacancy"); populate(g, 3);
        var pending = applications.apply(() -> g.user(3), g.course(), g.group(), UUID.randomUUID()).items().getFirst().id();
        var reverse = ordered(g, () -> leave(g, 1, UUID.randomUUID()),
                () -> applications.decide(() -> g.user(0), g.course(), pending, UUID.randomUUID(), "APPROVE"));
        assertInstanceOf(GroupingMemberships.Discovery.class, reverse.getFirst());
        assertInstanceOf(GroupingApplications.ApplicationPage.class, reverse.get(1)); assertEquals(3, occupancy(g)); integrity(g);
    }
    @Test void transferVersusTargetDepartureRechecksBothOrdersAndSuccessorIsCurrentMember() throws Exception {
        var f = fixture("life-race-target"); populate(f, 3);
        var outcomes = ordered(f, () -> leave(f, 1, UUID.randomUUID()), () -> transfer(f, 0, 1, UUID.randomUUID()));
        assertInstanceOf(GroupingMemberships.Discovery.class, outcomes.getFirst()); assertInstanceOf(CourseNotAccessible.class, outcomes.get(1));
        assertEquals(f.students().getFirst(), manager(f)); assertEquals(2, occupancy(f)); integrity(f);
        var reverse = ordered(f, () -> transfer(f, 0, 2, UUID.randomUUID()), () -> leave(f, 2, UUID.randomUUID()));
        reverse.forEach(o -> assertInstanceOf(GroupingMemberships.Discovery.class, o)); assertEquals(f.students().getFirst(), manager(f)); integrity(f);
    }
    @Test void transferDefeatsStaleApprovalAndStaleInvitationButKeepsOriginalPendingAttempt() throws Exception {
        var f = fixture("life-race-approval"); populate(f, 2);
        var attempt = applications.apply(() -> f.user(2), f.course(), f.group(), UUID.randomUUID()).items().getFirst().id();
        var approvalKey = UUID.randomUUID();
        var outcomes = ordered(f, () -> transfer(f, 0, 1, UUID.randomUUID()),
                () -> applications.decide(() -> f.user(0), f.course(), attempt, approvalKey, "APPROVE"));
        assertInstanceOf(GroupingMemberships.Discovery.class, outcomes.getFirst()); assertInstanceOf(CourseNotAccessible.class, outcomes.get(1));
        assertEquals(0, receipts(approvalKey)); assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM application_attempt WHERE id=?", String.class, attempt));
        applications.decide(() -> f.user(1), f.course(), attempt, UUID.randomUUID(), "APPROVE");
        var inviteKey = UUID.randomUUID();
        var reverse = ordered(f, () -> transfer(f, 1, 2, UUID.randomUUID()),
                () -> invitations.invite(() -> f.user(1), f.course(), f.group(), f.students().get(3), inviteKey));
        assertInstanceOf(GroupingMemberships.Discovery.class, reverse.getFirst()); assertInstanceOf(CourseNotAccessible.class, reverse.get(1));
        assertEquals(0, receipts(inviteKey)); assertEquals(3, occupancy(f)); integrity(f);
    }
    @Test void duplicateTransferKeyReplaysAfterAuthorityLossAndCompetingNewTransferIsDenied() throws Exception {
        var f = fixture("life-race-transfer"); populate(f, 3); var key = UUID.randomUUID();
        var outcomes = ordered(f, () -> transfer(f, 0, 1, key), () -> transfer(f, 0, 1, key));
        assertEquals(outcomes.getFirst(), outcomes.get(1)); assertEquals(1, receipts(key)); assertEquals(f.students().get(1), manager(f));
        var later = ordered(f, () -> transfer(f, 1, 2, UUID.randomUUID()), () -> transfer(f, 1, 0, UUID.randomUUID()));
        assertInstanceOf(GroupingMemberships.Discovery.class, later.getFirst()); assertInstanceOf(CourseNotAccessible.class, later.get(1)); integrity(f);
    }
    @Test void actualLockWaitCrossingDatabaseCutoffRollsBackDepartureAndTransferReceipts() throws Exception {
        for (boolean transfer : List.of(false, true)) {
            var f = fixture(transfer ? "life-wait-transfer" : "life-wait-leave"); populate(f, 2);
            var key = UUID.randomUUID();
            try (var pool = Executors.newSingleThreadExecutor(); var gate = lock(f)) {
                try (var s = gate.prepareStatement("UPDATE grouping_activity SET deadline=clock_timestamp()+interval '2 seconds' WHERE id=?")) {
                    s.setObject(1, f.activity()); assertEquals(1, s.executeUpdate());
                }
                var future = pool.submit(() -> transaction(() -> transfer ? transfer(f, 0, 1, key) : leave(f, 0, key)));
                awaitWaiters(pid(gate), 1);
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                try (var s = gate.createStatement()) {
                    while (true) {
                        try (var rows = s.executeQuery("SELECT clock_timestamp() >= deadline FROM grouping_activity WHERE id='" + f.activity() + "'::uuid")) {
                            assertTrue(rows.next()); if (rows.getBoolean(1)) break;
                        }
                        assertTrue(System.nanoTime() < until); Thread.sleep(20);
                    }
                }
                gate.commit(); assertEquals("ACTIVITY_CLOSED", assertInstanceOf(GroupingConflict.class, outcome(future)).code());
            }
            assertEquals(0, receipts(key)); assertEquals(2, occupancy(f)); assertEquals(f.students().getFirst(), manager(f)); integrity(f);
        }
    }
    @Test void enrollmentRevocationDuringActivityLockWaitRejectsFreshCommandAndSuccessfulReplay() throws Exception {
        var f = fixture("life-wait-access"); populate(f, 3); var key = UUID.randomUUID(); transfer(f, 0, 1, key);
        var before = snapshot(f);
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(f)) {
            var replay = pool.submit(() -> transaction(() -> transfer(f, 0, 1, key)));
            var fresh = pool.submit(() -> transaction(() -> leave(f, 1, UUID.randomUUID()))); awaitWaiters(pid(gate), 2);
            try (var s = gate.prepareStatement("DELETE FROM course_enrollment WHERE course_id=?")) {
                s.setObject(1, f.course()); assertEquals(6, s.executeUpdate());
            }
            gate.commit(); assertInstanceOf(CourseNotAccessible.class, outcome(replay)); assertInstanceOf(CourseNotAccessible.class, outcome(fresh));
        }
        assertEquals(before, snapshot(f)); integrity(f);
    }
    @Test void queuedDepartureRereadsCommittedDeadlineExtensionAfterOldCutoff() throws Exception {
        var f = fixture("life-race-extension"); populate(f, 2);
        var oldDeadline = jdbc.queryForObject("SELECT clock_timestamp()+interval '3 seconds'", OffsetDateTime.class).toInstant();
        jdbc.update("UPDATE grouping_activity SET deadline=?::timestamptz WHERE id=?", oldDeadline.toString(), f.activity());
        var extended = oldDeadline.plusSeconds(3600);
        var extensionSaved = new CountDownLatch(1); var releaseExtension = new CountDownLatch(1);
        var extensionPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var extension = pool.submit(() -> transaction(() -> {
                var saved = activities.extendDeadline(() -> f.teacher(), f.course(), UUID.randomUUID(), extended);
                extensionPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                extensionSaved.countDown();
                try { assertTrue(releaseExtension.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return saved;
            }));
            assertTrue(extensionSaved.await(10, TimeUnit.SECONDS));
            var departure = pool.submit(() -> transaction(() -> leave(f, 0, UUID.randomUUID())));
            try {
                awaitWaiters(extensionPid.get(), 1);
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT clock_timestamp() < ?::timestamptz", Boolean.class, oldDeadline.toString()))) {
                    assertTrue(System.nanoTime() < until); Thread.sleep(20);
                }
            } finally { releaseExtension.countDown(); }
            assertInstanceOf(GroupingActivities.Workspace.class, outcome(extension));
            assertInstanceOf(GroupingMemberships.Discovery.class, outcome(departure));
        }
        assertEquals(extended, activities.workspace(() -> f.teacher(), f.course()).activity().deadline());
        assertEquals(1, occupancy(f)); assertEquals(f.students().get(1), manager(f)); integrity(f);
    }
    @Test void departureAndDueAllocationShareActivityLockAndCutoffWinnerPreservesCommittedMembers() throws Exception {
        var f = fixture("life-race-allocation"); populate(f, 2);
        var cutoff = jdbc.queryForObject("SELECT clock_timestamp()+interval '2 seconds'", OffsetDateTime.class).toInstant();
        jdbc.update("UPDATE grouping_activity SET deadline=?::timestamptz WHERE id=?", cutoff.toString(), f.activity());
        var key = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2); var gate = lock(f)) {
            var leave = pool.submit(() -> transaction(() -> leave(f, 0, key))); awaitWaiters(pid(gate), 1);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT clock_timestamp() < ?::timestamptz", Boolean.class, cutoff.toString()))) {
                assertTrue(System.nanoTime() < until); Thread.sleep(20);
            }
            var allocation = pool.submit(() -> { dueWork.processDueWork(); return true; });
            awaitWaiters(pid(gate), 2); gate.commit();
            assertEquals("ACTIVITY_CLOSED", assertInstanceOf(GroupingConflict.class, outcome(leave)).code()); assertEquals(true, outcome(allocation));
        }
        assertTrue(jdbc.queryForObject("SELECT finalized_at IS NOT NULL FROM grouping_activity WHERE id=?", Boolean.class, f.activity()));
        assertEquals(0, receipts(key)); assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?", Integer.class, f.activity()));
        assertEquals(f.students().getFirst(), manager(f));
        assertEquals(f.group(), jdbc.queryForObject("SELECT group_id FROM activity_membership WHERE activity_id=? AND account_id=?", UUID.class, f.activity(), f.students().getFirst()));
        var before = snapshot(f); dueWork.processDueWork(); assertEquals(before, snapshot(f));
    }
}
