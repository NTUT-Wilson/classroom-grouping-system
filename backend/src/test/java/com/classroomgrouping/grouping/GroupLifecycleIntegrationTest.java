package com.classroomgrouping.grouping;

import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.grouping.api.*;
import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupLifecycleIntegrationTest extends GroupLifecycleTestSupport {
    @Test void ordinaryDepartureFreesOnePlaceRetainsManagerAndCompletedHistory() {
        var f = fixture("life-ordinary"); populate(f, 3);
        var attempts = jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id", f.activity());
        var result = leave(f, 1, UUID.randomUUID());
        assertNull(result.ownGroupId()); assertEquals(2, occupancy(f)); assertEquals(f.students().getFirst(), manager(f));
        assertEquals(attempts, jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id", f.activity()));
        integrity(f);
    }
    @Test void managerSuccessionUsesOnlyRemainingMembersAndCanRepeatToSingleSuccessor() {
        var f = fixture("life-successors"); populate(f, 4);
        var key = UUID.randomUUID(); leave(f, 0, key);
        UUID selected = manager(f); assertTrue(f.students().subList(1, 4).contains(selected));
        var before = snapshot(f); leave(f, 0, key); assertEquals(before, snapshot(f));
        int next = f.students().indexOf(selected); leave(f, next, UUID.randomUUID());
        UUID second = manager(f); assertNotEquals(selected, second); assertNotEquals(f.students().getFirst(), second);
        leave(f, f.students().indexOf(second), UUID.randomUUID());
        assertEquals(1, occupancy(f));
        assertEquals(jdbc.queryForObject("SELECT account_id FROM activity_membership WHERE activity_id=?", UUID.class, f.activity()), manager(f));
        integrity(f);
    }
    @Test void lastManagerEmptyingCancelsOnlyPendingRequestsAndGroupIsReusable() {
        var f = fixture("life-empty"); populate(f, 1);
        var oldApp = applications.apply(() -> f.user(1), f.course(), f.group(), UUID.randomUUID()).items().getFirst().id();
        applications.decide(() -> f.user(0), f.course(), oldApp, UUID.randomUUID(), "REJECT");
        applications.apply(() -> f.user(2), f.course(), f.group(), UUID.randomUUID());
        invitations.invite(() -> f.user(0), f.course(), f.group(), f.students().get(3), UUID.randomUUID());
        leave(f, 0, UUID.randomUUID()); assertNull(manager(f)); assertEquals(0, occupancy(f));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM application_attempt WHERE id=?", String.class, oldApp));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM application_attempt WHERE activity_id=? AND status='CANCELED' AND reason='Group empty'", Integer.class, f.activity()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM invitation_attempt WHERE activity_id=? AND status='CANCELED' AND reason='Group empty'", Integer.class, f.activity()));
        join(f, 4, UUID.randomUUID()); assertEquals(f.students().get(4), manager(f)); integrity(f);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM application_attempt WHERE activity_id=? AND status='PENDING'", Integer.class, f.activity()));
    }
    @Test void sameSuccessfulLeaveKeyCannotRemoveLaterRejoinedMembershipOrChangeSuccessor() {
        var f = fixture("life-return"); populate(f, 1); var key = UUID.randomUUID();
        leave(f, 0, key); join(f, 0, UUID.randomUUID()); var before = snapshot(f);
        assertEquals(f.group(), leave(f, 0, key).ownGroupId()); assertEquals(before, snapshot(f));
        assertEquals("COMMAND_KEY_REUSED", assertThrows(GroupingConflict.class,
                () -> memberships.leave(() -> f.user(0), f.course(), f.groups().get(1), key)).code());
        assertEquals(before, snapshot(f)); integrity(f);
    }
    @Test void vacancyDoesNotResurrectGroupFullAttemptsButNewAttemptIsEligible() {
        var f = fixture("life-vacancy"); populate(f, 3);
        var pending = applications.apply(() -> f.user(4), f.course(), f.group(), UUID.randomUUID()).items().getFirst().id();
        var fourth = applications.apply(() -> f.user(3), f.course(), f.group(), UUID.randomUUID()).items().getFirst().id();
        applications.decide(() -> f.user(0), f.course(), fourth, UUID.randomUUID(), "APPROVE");
        leave(f, 1, UUID.randomUUID());
        assertEquals("Group full", jdbc.queryForObject("SELECT reason FROM application_attempt WHERE id=?", String.class, pending));
        var newer = applications.apply(() -> f.user(4), f.course(), f.group(), UUID.randomUUID()).items().stream().filter(a -> a.status().equals("PENDING")).findFirst().orElseThrow();
        assertNotEquals(pending, newer.id()); integrity(f);
    }
    @Test void rejectedDepartureInputsAndRevokedSuccessfulReplayLeaveEverythingUnchanged() {
        var f = fixture("life-denied"); populate(f, 2); var before = snapshot(f);
        assertThrows(CourseNotAccessible.class, () -> leave(f, 3, UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> memberships.leave(() -> f.teacher(), f.course(), f.group(), UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> memberships.leave(() -> f.user(0), f.course(), f.groups().get(1), UUID.randomUUID()));
        assertThrows(GroupingValidationException.class, () -> leave(f, 1, null));
        assertEquals(before, snapshot(f)); var key = UUID.randomUUID(); leave(f, 1, key);
        jdbc.update("DELETE FROM course_enrollment WHERE course_id=? AND account_id=?", f.course(), f.students().get(1)); before = snapshot(f);
        assertThrows(CourseNotAccessible.class, () -> leave(f, 1, key)); assertEquals(before, snapshot(f));
    }
    @Test void departureReceiptCommitFailureRollsBackSuccessionAndEmptyRequestCleanup() {
        var f = fixture("life-rollback"); populate(f, 2); var key = UUID.randomUUID();
        persistenceFailure(f, key, () -> leave(f, 0, key)); leave(f, 0, key); assertEquals(f.students().get(1), manager(f));
        applications.apply(() -> f.user(2), f.course(), f.group(), UUID.randomUUID());
        invitations.invite(() -> f.user(1), f.course(), f.group(), f.students().get(3), UUID.randomUUID());
        var emptyKey = UUID.randomUUID(); persistenceFailure(f, emptyKey, () -> leave(f, 1, emptyKey));
        leave(f, 1, emptyKey); assertNull(manager(f)); assertEquals(1, receipts(emptyKey)); integrity(f);
    }
    @Test void transferChangesAuthorityOnlyAndReplayCannotTransferAgainAfterRoleLoss() {
        var f = fixture("life-transfer"); populate(f, 3);
        applications.apply(() -> f.user(3), f.course(), f.group(), UUID.randomUUID());
        invitations.invite(() -> f.user(0), f.course(), f.group(), f.students().get(4), UUID.randomUUID());
        var members = jdbc.queryForList("SELECT * FROM activity_membership WHERE activity_id=? ORDER BY account_id", f.activity());
        var apps = jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id", f.activity());
        var invites = jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id", f.activity());
        var key = UUID.randomUUID(); transfer(f, 0, 1, key);
        assertEquals(f.students().get(1), manager(f)); assertEquals(3, occupancy(f));
        assertEquals(members, jdbc.queryForList("SELECT * FROM activity_membership WHERE activity_id=? ORDER BY account_id", f.activity()));
        assertEquals(apps, jdbc.queryForList("SELECT * FROM application_attempt WHERE activity_id=? ORDER BY id", f.activity()));
        assertEquals(invites, jdbc.queryForList("SELECT * FROM invitation_attempt WHERE activity_id=? ORDER BY id", f.activity()));
        transfer(f, 1, 2, UUID.randomUUID()); var before = snapshot(f); transfer(f, 0, 1, key); assertEquals(before, snapshot(f));
        assertEquals("COMMAND_KEY_REUSED", assertThrows(GroupingConflict.class, () -> transfer(f, 0, 2, key)).code());
        assertEquals(before, snapshot(f)); integrity(f);
    }
    @Test void transferRejectsSelfOtherGroupDepartedAndNonmanagerTargetsAtomically() {
        var f = fixture("life-transfer-denied"); populate(f, 3);
        memberships.joinEmpty(() -> f.user(4), f.course(), f.groups().get(1), UUID.randomUUID());
        leave(f, 2, UUID.randomUUID()); var before = snapshot(f);
        assertThrows(GroupingValidationException.class, () -> transfer(f, 0, 0, UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> transfer(f, 0, 4, UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> transfer(f, 0, 2, UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> transfer(f, 1, 0, UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> memberships.transferManager(() -> f.teacher(), f.course(), f.group(), UUID.randomUUID(), f.students().get(1)));
        assertEquals(before, snapshot(f)); integrity(f);
    }
    @Test void transferReceiptCommitFailureRollsBackAuthorityAndAllOtherState() {
        var f = fixture("life-transfer-failure"); populate(f, 2); var key = UUID.randomUUID();
        persistenceFailure(f, key, () -> transfer(f, 0, 1, key));
        assertEquals(f.students().getFirst(), manager(f)); transfer(f, 0, 1, key); assertEquals(f.students().get(1), manager(f));
        assertEquals(1, receipts(key)); integrity(f);
    }
    @Test void successfulLifecycleKeysAreBoundToSourceCourseAndGroup() {
        var f = fixture("life-key-scope"); populate(f, 2); var transferKey = UUID.randomUUID(); transfer(f, 0, 1, transferKey);
        var leaveKey = UUID.randomUUID(); leave(f, 0, leaveKey);
        String code = f.prefix() + "-other";
        var seedAccounts = new ArrayList<com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount>();
        seedAccounts.add(new com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount(f.teacher(), "synthetic-password", "TEACHER", null, null));
        for (int i = 0; i < 6; i++) seedAccounts.add(new com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount(f.user(i), "synthetic-password", "STUDENT", f.user(i), "Synthetic " + i));
        provisioning.provision(new com.classroomgrouping.courses.api.RosterProvisioning.Roster(seedAccounts, List.of(
                new com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse(code, "Other lifecycle", List.of(f.teacher()),
                        java.util.stream.IntStream.range(0, 6).mapToObj(f::user).toList()))));
        UUID course = jdbc.queryForObject("SELECT id FROM course WHERE code=?", UUID.class, code);
        var other = activities.create(() -> f.teacher(), course, UUID.randomUUID(), new GroupingActivities.CreateActivity(
                "Other", java.time.Instant.now().plusSeconds(3600), List.of(4, 2))).activity();
        var before = snapshot(f);
        assertEquals("COMMAND_KEY_REUSED", assertThrows(GroupingConflict.class, () -> memberships.leave(() -> f.user(0), course, other.groups().getFirst().id(), leaveKey)).code());
        assertEquals("COMMAND_KEY_REUSED", assertThrows(GroupingConflict.class, () -> memberships.transferManager(() -> f.user(0), course, other.groups().getFirst().id(), transferKey, f.students().get(1))).code());
        assertEquals("COMMAND_KEY_REUSED", assertThrows(GroupingConflict.class, () -> memberships.transferManager(() -> f.user(0), f.course(), f.groups().get(1), transferKey, f.students().get(1))).code());
        assertThrows(CourseNotAccessible.class, () -> memberships.leave(() -> f.user(1), f.course(), other.groups().getFirst().id(), UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> memberships.transferManager(() -> f.user(1), f.course(), other.groups().getFirst().id(), UUID.randomUUID(), f.students().get(2)));
        assertEquals(before, snapshot(f)); assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?", Integer.class, other.id()));
    }
    @Test void lateEnrollmentDoesNotGrantFixedParticipationForEitherLifecycleCommand() {
        var f = fixture("life-late"); populate(f, 2); String late = f.prefix() + "-late";
        provisioning.provision(new com.classroomgrouping.courses.api.RosterProvisioning.Roster(List.of(
                new com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount(f.teacher(), "synthetic-password", "TEACHER", null, null),
                new com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount(late, "synthetic-password", "STUDENT", late, "Late synthetic")),
                List.of(new com.classroomgrouping.courses.api.RosterProvisioning.SeedCourse(f.prefix(), "Lifecycle " + f.prefix(), List.of(f.teacher()), List.of(late)))));
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM course_enrollment WHERE course_id=?", Integer.class, f.course()));
        var before = snapshot(f);
        assertThrows(CourseNotAccessible.class, () -> memberships.leave(() -> late, f.course(), f.group(), UUID.randomUUID()));
        assertThrows(CourseNotAccessible.class, () -> memberships.transferManager(() -> late, f.course(), f.group(), UUID.randomUUID(), f.students().getFirst()));
        assertEquals(before, snapshot(f)); integrity(f);
    }
    @Test void allocationConsumesLifecycleResultAndReplaysNeverAlterFinalizedMembershipsOrSavedPlan() {
        var f = fixture("life-allocation"); populate(f, 3);
        var transferKey = UUID.randomUUID(); transfer(f, 0, 1, transferKey); var leaveKey = UUID.randomUUID(); leave(f, 0, leaveKey);
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?", f.activity());
        dueWork.processDueWork();
        assertTrue(jdbc.queryForObject("SELECT finalized_at IS NOT NULL FROM grouping_activity WHERE id=?", Boolean.class, f.activity()));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM activity_membership WHERE activity_id=?", Integer.class, f.activity()));
        assertEquals(f.students().get(1), manager(f));
        assertEquals(4, jdbc.queryForObject("SELECT placement_count FROM allocation_plan WHERE activity_id=?", Integer.class, f.activity()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM allocation_placement WHERE activity_id=? AND account_id=?", Integer.class, f.activity(), f.students().getFirst()));
        var before = snapshot(f); dueWork.processDueWork();
        assertEquals("FINALIZED", leave(f, 0, leaveKey).status()); assertEquals("FINALIZED", transfer(f, 0, 1, transferKey).status());
        assertEquals("ACTIVITY_CLOSED", assertThrows(GroupingConflict.class, () -> leave(f, 1, UUID.randomUUID())).code());
        assertEquals(before, snapshot(f));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE allocation_placement SET group_id=? WHERE activity_id=? AND account_id=?", f.groups().get(1), f.activity(), f.students().getFirst()));
        assertEquals(before, snapshot(f));
    }
    @Test void cutoffDeniesNewCommandsWhileSuccessfulReplayReturnsPrivateCurrentProjection() {
        var f = fixture("life-cutoff"); populate(f, 2); var key = UUID.randomUUID(); transfer(f, 0, 1, key);
        jdbc.update("UPDATE grouping_activity SET deadline=clock_timestamp()-interval '1 second' WHERE id=?", f.activity()); var before = snapshot(f);
        assertEquals("ACTIVITY_CLOSED", assertThrows(GroupingConflict.class, () -> leave(f, 1, UUID.randomUUID())).code());
        assertEquals("ACTIVITY_CLOSED", assertThrows(GroupingConflict.class, () -> transfer(f, 1, 0, UUID.randomUUID())).code());
        var replay = transfer(f, 0, 1, key); assertEquals("FINALIZING", replay.status()); assertNull(replay.ownGroupId()); assertTrue(replay.groups().isEmpty());
        assertEquals(before, snapshot(f));
    }
    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private HttpResponse<String> request(HttpClient c, String path, String method, String body, String csrf, String type) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf); if (type != null) builder.header("Content-Type", type);
        return c.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String csrf(HttpClient c) throws Exception { return JsonPath.read(request(c, "/api/auth/csrf", "GET", "", null, null).body(), "$.token"); }
    private HttpClient login(String user) throws Exception {
        var c = client(); assertEquals(204, request(c, "/api/auth/login", "POST", "username=" + user + "&password=synthetic-password", csrf(c), "application/x-www-form-urlencoded").statusCode()); return c;
    }
    @Test void browserCommandsRequireSessionStudentRoleCsrfAndStrictRedactedDtos() throws Exception {
        var f = fixture("life-http"); populate(f, 2);
        String leave = "/api/courses/" + f.course() + "/groups/" + f.group() + "/leave";
        String transfer = leave.replace("/leave", "/manager-transfer"); String key = UUID.randomUUID().toString();
        var anonymous = client(); var manager = login(f.user(0)); var teacher = login(f.teacher());
        var before = snapshot(f);
        for (String path : List.of(leave, transfer)) {
            String body = "{\"commandKey\":\"" + key + "\",\"targetMemberId\":\"" + f.students().get(1) + "\"}";
            if (path.equals(leave)) body = "{\"commandKey\":\"" + key + "\"}";
            assertEquals(401, request(anonymous, path, "POST", body, csrf(anonymous), "application/json").statusCode());
            assertEquals(403, request(teacher, path, "POST", body, csrf(teacher), "application/json").statusCode());
            assertEquals(403, request(manager, path, "POST", body, null, "application/json").statusCode());
        }
        for (String body : List.of("{}", "{\"commandKey\":5}", "{\"commandKey\":\"1-1-1-1-1\"}", "{\"commandKey\":\""+key+"\",\"studentId\":\"someone\"}"))
            assertEquals(400, request(manager, leave, "POST", body, csrf(manager), "application/json").statusCode());
        for (String body : List.of("{\"commandKey\":\""+key+"\"}", "{\"commandKey\":\""+key+"\",\"targetMemberId\":5}", "{\"commandKey\":\""+key+"\",\"targetMemberId\":\"1-1-1-1-1\"}"))
            assertEquals(400, request(manager, transfer, "POST", body, csrf(manager), "application/json").statusCode());
        var expired = login(f.user(1)); assertEquals(1, jdbc.update("DELETE FROM spring_session WHERE principal_name=?", f.user(1)));
        assertEquals(401, request(expired, leave, "POST", "{\"commandKey\":\""+key+"\"}", csrf(expired), "application/json").statusCode());
        assertEquals(401, request(expired, transfer, "POST", "{\"commandKey\":\""+key+"\",\"targetMemberId\":\""+f.students().getFirst()+"\"}", csrf(expired), "application/json").statusCode());
        assertEquals(before, snapshot(f));
        assertEquals(200, request(manager, transfer, "POST", "{\"commandKey\":\""+key+"\",\"targetMemberId\":\""+f.students().get(1)+"\"}", csrf(manager), "application/json").statusCode());
        assertEquals(200, request(manager, leave, "POST", "{\"commandKey\":\""+UUID.randomUUID()+"\"}", csrf(manager), "application/json").statusCode());
        assertEquals("LeaveGroup[redacted]", new GroupingMembershipsController.LeaveGroup(null).toString());
        assertEquals("TransferManager[redacted]", new GroupingMembershipsController.TransferManager(null, null).toString()); integrity(f);
    }
}
