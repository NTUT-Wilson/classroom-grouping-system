# Classroom Grouping System — V1 PRD

**Project-wide amendment (2026-10-19):** The English-only United States university assumption in [Project assumptions](project-assumptions.md) applies to V1, V2, and future requirements, architecture, flows, and acceptance. No multilingual support, translation, localization, or cross-language retrieval is required. This is not a nationality or native-language verification requirement. All deadline entry and display use America/New_York with daylight-saving handling; reject nonexistent and ambiguous wall times. Backend validation compares instants against authoritative database time.

## 1. Document status and sources

**V2 planning:** The [confirmed V2 PRD](v2-PRD.md) explicitly amends lifecycle behavior for V2 and adds announcements/course assistance. Its exclusions retain no manual application withdrawal or invitation cancellation. V1 exclusions below remain V1 requirements; V2 planning does not implement features or change this version's permissions. Project-wide English/New York amendments above apply to both versions.

**Status:** V1 product scope confirmed by the user on 2026-10-12. The product grilling workflow is complete. Product behavior through Q72 is recorded below, with superseded proposals identified. Technical design and Project Foundation validation remain pending; scope confirmation does not authorize implementation, project initialization, or deployment.

Sources:

- [Product Definition](product-definition.md): product purpose, capabilities, scope, and permissions.
- [System Architecture](architecture.md): confirmed technical boundaries, provisional deployment, and technical decisions still open.
- [User Flows](user-flows.md): all nine reviewed flows, confirmed outcomes, and deferred technical design. Mermaid source remains authoritative.

Status labels used in this document:

- **Confirmed:** Approved capability, constraint, or behavior from the sources.
- **Technical design remaining:** An implementation choice to resolve during architecture/foundation while preserving confirmed product behavior.
- **Provisional:** A proposed technical choice awaiting validation.

Acceptance checks describe the confirmed product behavior and must preserve the shared security, concurrency, failure, and privacy requirements. Confirmed answers are reflected in the supporting documents. No additional product question is currently identified; Section 9 lists deferred technical work.

**Workflow closure:** On 2026-10-12, the user confirmed that this PRD and its supporting documents match the shared understanding of V1 and declared the product grilling process complete. Confirmed terminology is captured in [GLOSSARY.md](../GLOSSARY.md). Closing this review records that confirmation only; it makes no new product or technical decision.

Confirmed review answers:

- **Q1 — Course/activity cardinality:** Each course may have at most one grouping activity in V1. A course may have none. Multiple grouping activities for the same course are outside V1 scope.
- **Q2 — Membership uniqueness:** A student may belong to at most one group at a time within a grouping activity. A student with no membership in that activity is ungrouped. Q14, Q15, and Q22/Q28 define applicant, invitation recipient, and empty-group join eligibility; Q23 excludes voluntary departure. Q40 requires every eligible student to belong to one group before the roster is finalized.
- **Q3 — Historical student-creation membership (superseded by Q28):** Previously, student creation established the creator's membership. V1 now uses system-created groups and deliberate first joining under Q28.
- **Q4 — Application approval:** Joining through an application requires approval from an authorized student in the target group. Applications remain pending until a permitted response or another subsequently approved lifecycle outcome; approval requires fresh eligibility and capacity checks before membership is established. Q5 restricts the reviewer to the group manager; teachers cannot review or approve applications.
- **Q5 — Application reviewer:** Only the target group's manager may approve or reject join applications. Other members and teachers do not have this authority. Activity reset is excluded under Q59; no additional management capability has been confirmed beyond the permissions stated here; transfer and delegation of manager authority are excluded under Q25.
- **Q6 — Invitation sender:** Only the target group's manager may send invitations for that group. Other members and teachers cannot send them. Q71 and the confirmed request lifecycle govern eligibility; activity reset is excluded under Q59.
- **Q7 — Invitation capacity:** Pending invitations do not reserve group places. Capacity is checked when the recipient accepts and membership is established; another permitted join may fill the group first. Q9, Q56, and Q40 determine cancellation after joining, cancellation because the group fills, and deadline expiry; completed requests cannot be accepted.
- **Q8 — Application capacity:** Pending join applications do not reserve group places. Another permitted join may use the capacity before approval; current capacity is checked when approval establishes membership. Q9, Q56, and Q40 determine cancellation after joining, cancellation because the group fills, and deadline expiry; completed requests cannot be approved.
- **Q9 — Pending-request cleanup:** Establishing membership automatically cancels that student's other pending join applications and received invitations within the same grouping activity. A request used to establish membership retains its accepted outcome. This applies to empty-group joining, application approval, invitation acceptance, and system assignment; it does not cancel requests in other activities or invitations the student sent to others.
- **Q10 — Applications to different groups:** An ungrouped student may have pending join applications to multiple different groups in the same activity. These do not reserve places, and joining cancels the other pending requests under Q9. Q11 prevents same-group duplicates, Q13 permits reapplication after rejection, and Q14 restricts applicants to ungrouped students; Q66 excludes an aggregate pending-request cap; other outcomes remain governed by current eligibility, and Q67 preserves completed attempt history.
- **Q11 — Duplicate pending applications:** A student may have at most one pending application to a given group. Repeated or concurrent submissions while that application is pending cannot create additional pending applications for the same student and group. Q13 permits reapplication after rejection; Other completed outcomes do not bypass current eligibility; Q67 preserves each completed attempt's history.
- **Q12 — No applicant withdrawal:** Students cannot manually withdraw pending join applications in V1; they wait for the recorded outcome. Q9 automatic cancellation upon joining still applies. Q39 stops student actions at the activity deadline; Q40 expires pending requests at the deadline; Q65 excludes earlier expiration; voluntary departure is excluded under Q23; Q21 separately excludes manager cancellation of invitations.
- **Q13 — Reapplication after rejection:** V1 allows students to apply to the same group again after rejection, subject to current eligibility and the one-pending-application rule. A previous rejection alone does not block reapplication. Q67 requires completed-attempt history, and Q66 excludes an aggregate pending-request cap. Every new attempt requires current eligibility; persistence design remains technical, and no cooldown is selected.
- **Q14 — Applicant membership eligibility:** Only students without membership in the grouping activity may submit join applications, including reapplications. Already-grouped students cannot submit applications in that activity; submitting an application cannot switch or remove their existing membership.
- **Q15 — Invitation recipient membership eligibility:** Invitations may be sent only to classmates with no group membership in that activity. Already-grouped classmates cannot receive a new invitation for that activity. Current eligibility is rechecked at acceptance; membership established in the meantime cancels the pending invitation under Q9.
- **Q16 — Duplicate pending invitations:** A group may have at most one pending invitation for a particular student. Repeated or concurrent sends while that invitation is pending cannot create another pending invitation for the same group and recipient. Q17 permits reinvitation after rejection and Q18 permits invitations from different groups; Other completed outcomes do not bypass current eligibility; Q67 preserves each completed attempt's history.
- **Q17 — Reinvitation after rejection:** V1 allows a group manager to invite the same student again after the student rejects an invitation, subject to current eligibility and one pending invitation per group and recipient. Prior rejection alone does not block another invitation. Q66 excludes aggregate pending-request caps; Q67 requires completed-attempt history. Every new send needs current eligibility; persistence design remains technical.
- **Q18 — Invitations from different groups:** An ungrouped student may have pending invitations from multiple different groups in the same activity. A pending invitation from one group does not by itself prevent another group's manager from inviting that student. Joining through any permitted route cancels the student's other pending requests under Q9; Q66 excludes an aggregate pending-request cap.
- **Q19 — Application/invitation coexistence:** A join application and group invitation may both be pending for the same student and group. Creating one neither blocks nor automatically accepts the other and does not establish membership. Successful joining through either route cancels the other pending request under Q9. Q20 confirms rejection affects only the request answered.
- **Q20 — Independent rejection outcomes:** Rejecting an application or invitation changes only that request; a pending counterpart for the same student and group remains unchanged. Rejecting an invitation does not withdraw an application, and rejecting an application does not revoke an invitation. The counterpart may still establish membership through its permitted joining route, subject to current checks.
- **Q21 — No manager invitation cancellation:** Group managers cannot manually cancel pending invitations in V1. Recipients may still respond subject to current checks, and Q9 automatic cancellation upon joining still applies. Q40 expires still-pending requests at the activity deadline; Q65 excludes earlier expiration; Q9/Q56/Q40 determine join cancellation, full-group cancellation, and deadline expiry.
- **Q22 — Ungrouped-only eligibility, revised by Q28:** Student group creation is superseded. Only an ungrouped student may deliberately join an empty system-created group; joining cannot remove or switch an existing membership and applies Q9 cleanup.
- **Q23 — Voluntary departure:** Both ordinary members and managers cannot voluntarily leave their group in V1. This is a temporary scope exclusion; voluntary departure is a prioritized V2 feature. Self-service switching cannot bypass this restriction. V2 timing, manager succession, empty-group behavior, and other departure rules are not yet specified; Q24 excludes manager member removal; activity reset is excluded under Q59.
- **Q24 — No manager member removal:** Group managers cannot remove existing members in V1. No member-removal UI or direct request may grant this permission; rejection of an application is not removal of an existing member. Voluntary departure remains prioritized for V2 under Q23; this does not independently prioritize or specify a V2 member-removal feature.
- **Q25 — Initial manager retains authority (role revised by Q28):** The initial group manager alone approves or rejects applications and sends invitations in V1. This authority cannot be transferred or delegated; Q28 replaces the former student-creator role.
- **Q25a — Candidate recommendations:** Members identify candidates through permitted student discovery and tell the manager in person or through an existing communication channel they use. V1 has no in-app candidate-suggestion workflow or messaging integration; recommendations do not give members approval or invitation authority.
- **Q26 — Intended sizes with remainder adjustment (configuration clarified by Q29):** Groups need not share one exact size: the confirmed example for 9 students is 2, 2, 2, 3. A normal target of 2 describes that example rather than every group's maximum. Q28 makes each predetermined capacity the joining limit; Q29 selects manual capacity-list entry rather than system calculation from a target.
- **Q27 — Unanswered prior proposal:** Restricting above-target joining to system assignment was not approved. With Q28, students join within each group's own predetermined capacity; no separate system overflow permission is confirmed.
- **Q28 — System-created groups and initial manager:** The system creates the activity's groups with predetermined capacities before students participate. Students do not create groups. The first ungrouped student deliberately joining an empty group becomes its initial group manager and member. Applications to occupied groups require that manager's approval; invitations and all other confirmed request rules remain. Q29 confirms that the teacher enters the capacity list; Q32 confirms creation together with the activity; Q46 leaves groups first populated by system assignment without a manager.
- **Q29 — Manual capacity layout:** The teacher manually enters each group's capacity as a list, such as 2, 2, 2, 3; the system creates one group for each entry with that capacity. V1 does not calculate the layout from a normal target size or automatically redistribute a remainder. Q30 requires total capacity to equal the eligible student count. Q31 requires each entry to be a whole number of at least 2. Q32 confirms creation together with the activity. Q34 fixes the capacity list after creation. Q41 fixes the participant list and count at successful creation; creation-time consistency enforcement remains technical design work; a separate target-size input is not required by this decision.

- **Q30 — Exact total capacity:** The teacher-entered capacities must sum exactly to the number of eligible students in the activity. A list with fewer or more places is rejected with correction feedback; the system does not silently adjust it. For nine eligible students, 2, 2, 2, 3 satisfies this total rule, while totals of 8 or 10 do not. Q31 confirms the minimum and whole-number rule; no additional size limit is selected; creation-time consistency enforcement remains technical design work. Capacity-list changes after creation are excluded under Q34. This validation alone guarantees neither completed grouping nor finalization.

- **Q31 — Minimum and whole-number capacities:** Every group capacity must be a whole number of at least 2. Zero, negative, fractional, and single-student capacities are rejected with correction feedback, even if the list total otherwise matches the eligible student count. Q30's exact-total rule also applies. No additional maximum capacity is selected; creation-time consistency enforcement remains technical design work; Q71/Q72 close the remaining product edge cases.

- **Q32 — Activity and group creation together:** A successful activity-creation submission saves the activity and all groups from its approved capacity list together in one transaction. Each list entry creates one group immediately, without a separate generation step. A definitive creation failure saves neither the activity nor any of those groups. After a lost response, actual committed state determines success. Q33 confirms immediate visibility to eligible students and opening for grouping; Q39 stops all student grouping actions at the deadline; Q40 confirms deadline expiry, remaining-student allocation, and system finalization; cutoff races and allocation/recovery details remain unresolved.

- **Q33 — Immediate visibility and opening:** Immediately after successful activity/group creation, eligible students can see the activity and begin permitted grouping actions. V1 has no separate publish step, saved draft lifecycle, or scheduled opening time. Teachers must check instructions and capacities before submitting. Enrollment/resource checks, current joining rules, and subsequently decided deadline rules still apply; Q34 fixes capacities after creation; Q35 permits instructions editing while grouping is open; Q36 permits deadline extension while open; Q38 excludes extension/reopening after expiry; Q40 confirms teacher final-roster availability after completion; Q45 confirms student own-group access at system finalization; Q51/Q53/Q57/Q58/Q67 specify fields and reading permissions.

- **Q34 — Capacity layout fixed after creation:** The group-capacity list is immutable after successful activity creation in V1, even before any student joins. No subsequent edit may change individual capacities or add, remove, or replace entries/groups to alter the layout. Disallowed requests leave groups, memberships, and pending requests unchanged. Q35 permits instructions editing while grouping is open. Q36 permits deadline extension while open. Q38 excludes extension/reopening after expiry. Activity deletion/replacement/reset is excluded under Q59; Q34 grants no reset bypass.

- **Q35 — Edit activity instructions while open:** The teacher assigned to the course may edit the activity's task description and guidance while grouping is open. This changes the instruction text only, without changing capacities, groups, memberships, requests, or the deadline. Q61 also permits instruction-text editing after the deadline and finalization; Q60 resolves notifications; Q63 excludes user-facing description history and restore. Capacity-layout editing remains excluded under Q34; Q36 separately permits deadline extension while grouping is open.

- **Q36 — Extend deadline while open:** The assigned teacher may extend the activity's deadline while grouping is still open. The submitted new deadline must be later than the current deadline at validation. A successful extension changes the deadline only, preserving instructions, fixed capacities, groups, memberships, and request outcomes. Q37 excludes deadline shortening. Q38 excludes extension/reopening after the current deadline passes. Q55 fixes the backend-time eligibility cutoff; concurrency implementation remains technical design work; no deadline-history feature has been selected. Q60 resolves notification scope.

- **Q37 — No deadline shortening:** Teachers cannot move an activity's deadline earlier in V1, including while grouping is still open. Such edits are rejected without changing the current deadline or other activity/group/request state; no UI or direct request may bypass the restriction. Q36 still permits a later deadline while grouping remains open. Q38 excludes extension/reopening after expiry; Q39 confirms the student-action cutoff; Q40 confirms pending-request expiry, system allocation, and finalization; execution and recovery details remain unresolved.

- **Q38 — No extension or reopening after expiry:** V1 does not allow extending the deadline or reopening grouping once the current deadline has passed. A permitted extension under Q36 must occur before the current deadline while grouping remains open. Disallowed late-extension or reopening requests preserve the deadline, activity/group state, memberships, and request outcomes. Q39 confirms the student-action cutoff. Q40 confirms expiry, automatic allocation, and finalization; Q55 fixes the backend-time cutoff rule; concurrency implementation and execution/recovery details remain unresolved; this decision grants no activity reset bypass.

- **Q39 — Student grouping cutoff:** At the activity's current deadline, all student grouping actions stop: deliberate empty-group joining, application submission/reapplication, application approval or rejection, invitation sending/reinvitation, and invitation acceptance or rejection. Backend deadline checks apply to direct requests and stale UI; late actions cannot change memberships or requests. Q40 confirms pending-request expiry and deadline-triggered allocation/finalization. Q53 retains course peer-profile reading after the deadline; Q55 fixes in-flight request eligibility; Q57–Q58 fix request and pre-deadline membership reading; Q51/Q53/Q57/Q58/Q67 fix reading permissions; concurrency implementation remains technical design work.

- **Q40 — Deadline expiry, complete assignment, and final roster:** At the current deadline, all still-pending applications and invitations expire automatically. Expiration is distinct from rejection and membership-triggered cancellation; completed accepted, rejected, canceled, or expired outcomes are preserved. The system then automatically assigns every remaining eligible ungrouped student to available places in the existing groups, respecting capacities and preserving established memberships. No student application, invitation, or manager approval is required for this system assignment. Once every eligible student has exactly one membership and completion succeeds, the system finalizes the roster and makes it available for the assigned teacher to view, obtain, and export, with no teacher assignment/finalization step. This does not select a push notification or promise processing is instantaneous; failures/incomplete processing cannot be presented as a final roster. Q41 fixes the participant list at creation; Q43 confirms random student ordering; cutoff coordination and execution/recovery implementation remain technical design work. Member identities use the confirmed Q51 profile fields; additional fields have not been selected.

- **Q41 — Fixed activity participant list:** Successful activity creation fixes its eligible participant list and validates capacities against that list's count. All subsequent grouping eligibility and remaining-student allocation use this fixed activity list; later enrollment changes do not automatically add or remove participants or alter the count. V1 provides no participant-list editing after creation. Q42 includes every student enrolled in the course at creation; creation-time consistency enforcement remains technical design work; this decision authorizes no enrollment-management feature or activity reset bypass.

- **Q42 — All enrolled students participate:** The initial activity participant list automatically includes every student enrolled in its course when creation succeeds, whether or not they have logged in or taken a grouping action. Teachers cannot select a subset or exclude individual enrolled students in V1. Validate total capacity against that complete list, fix it under Q41, and include every participant in Q40 completion. Later enrollment changes do not alter this activity; membership, permission, capacity, and deadline checks still govern individual actions.

- **Q43 — Random remaining-student allocation:** At deadline completion, the system randomly shuffles the remaining ungrouped activity participants and fills existing empty group places in that order. Existing memberships and fixed capacities are preserved. Previous applications and invitations do not influence assignment, regardless of their outcomes; no teammate preference, ranking, or AI matching is introduced. Q44 requires retries to reuse the chosen plan. Slot enumeration, randomness implementation, and execution/recovery details remain unresolved.

- **Q44 — Preserve the random plan on retries:** Once a random allocation plan has been chosen for this activity's deadline completion, failures, lost responses, and retries must not reshuffle students or choose different placements. Retain the student-to-group plan before applying assignments; retries reconcile actual saved memberships and complete the same planned placements without duplicates or moving already-assigned students. Repeated or concurrent processing must not create competing plans or memberships. Finalization still requires complete successful assignment under Q40. Plan-retention design, retry trigger/backoff, transaction scope, and inconsistency recovery remain unresolved.

- **Q45 — Student final results available at finalization:** When the system successfully finalizes the complete roster, each activity participant can view their own final group and permitted member information at the same time the assigned teacher gains full-roster access. This includes students placed by system assignment. Before complete finalization, no view may label in-progress assignments as final. Students gain no teacher-style full final-roster access; authentication, participant/resource checks, and member-field privacy still apply. This grants no push notification, export right for students, or broader post-deadline discovery permission.

- **Q46 — No manager for system-only groups:** A group that remains empty until post-deadline system assignment has no group manager in V1. The system does not appoint the first allocated student or any other student as manager. Existing managers remain unchanged when remaining places are filled. Managerless groups are valid finalized groups; they need no manager approval for assignment or finalization. Q39 has already closed all application/invitation actions; no broader team-leader role is introduced.

- **Q47 — Automatic fixed group names:** At activity creation, the system names groups Group 1, Group 2, and so on in the teacher-entered capacity-list order. The list 2, 2, 2, 3 creates Group 1 through Group 4 with those respective capacities. Names remain stable within that activity; teachers and students cannot supply custom names or rename groups in V1. These labels apply to discovery, applications/invitations, own-group results, and the teacher's final roster; no global name uniqueness is required across activities.

- **Q48 — CSV final-roster download:** The assigned teacher may download the successfully finalized full grouping roster as a CSV file in V1. The export uses the finalized activity result, remains subject to teacher/course authorization and final-result availability, and grants no student export or full-roster access. Q49 confirms one row per participant and exactly three columns: Group name, Student ID, Student name. Q50 sorts rows by numeric group number, then Student ID ascending. Encoding/dialect, filename, and output consistency details remain unresolved; this decision creates no push delivery or separate file-storage requirement.

- **Q49 — CSV rows and columns:** The teacher's CSV export contains one row per activity participant, with exactly these columns in order: Group name, Student ID, Student name. Student ID means the institutional student number. Each participant appears exactly once with their finalized group; the group name repeats for its members, and both self-grouped and system-assigned students are included. Q49 confirms teacher-export identity fields; Q51 separately permits classmates to see name, institutional Student ID, and course-specific About me in permitted peer-profile views. Q50 confirms row ordering; remaining CSV output details remain unresolved.

- **Q50 — CSV row ordering:** Sort exported rows by ascending group number, then by ascending institutional Student ID within each group. Group 2 precedes Group 10; sort by the group's number rather than the displayed name as plain text. Repeated downloads of the same finalized data use the same row order, independent of random allocation or database retrieval order. CSV encoding/dialect, filename, and other output details remain unresolved.

- **Q51 — Course-specific About me and peer profile fields:** V1 classmates may see Student name, institutional Student ID, and an optional About me for that course as basic profile information, alongside separately permitted grouping information. Each student may edit only their own course-specific plain-text introduction, with free-form wording and an empty string allowed. The maximum length is 500 characters; longer text is rejected with correction feedback. About me is separate for each course and affects neither membership, requests, fixed capacities/participants, nor the random allocation plan. CSV remains the three columns confirmed in Q49. Q52 permits own About me editing after the grouping deadline. Q53 retains peer-profile discovery after the deadline; Q51/Q53/Q57/Q58/Q67 specify fields and reading permissions; extra fields have not been selected.

- **Q52 — About me editing after deadline:** Students may continue editing their own course-specific About me after the grouping deadline, including after roster finalization, subject to normal authentication, course/resource access, ownership, CSRF, and the 0–500-character plain-text rule. This is a profile edit rather than a grouping action: it does not reopen grouping, change finalized memberships/rosters, alter request outcomes or the allocation plan, or add About me to CSV. Q53 separately permits peer-profile discovery after the deadline, keeping final membership details restricted to the student's own group.

- **Q53 — Peer-profile discovery after deadline:** Students may continue browsing same-course classmates' Student names, institutional Student IDs, and current course-specific About me after the grouping deadline and finalization, subject to authentication and course/resource access. This does not reopen grouping or expose other groups' final membership lists, another student's final group assignment, or the teacher's full roster. Students see final membership details only for their own group; teachers retain full-roster access. Q57 resolves private request reading and Q58 resolves pre-deadline membership discovery; Q51/Q53/Q57/Q58/Q67 specify fields and reading permissions; extra fields have not been selected.

- **Q54 — Required future initial deadline:** Activity creation requires a deadline strictly in the future according to backend time; missing or nonfuture values are rejected with correction feedback.

- **Q55 — No deadline grace period:** Student grouping mutations must pass the final backend eligibility check immediately before saving strictly before the current deadline, regardless of client submission time. This fixes eligibility timing; it does not prescribe a physical database commit timestamp or concurrency mechanism.

- **Q56 — Full-group request closure:** Reject new applications and invitations when the group is full. When joining fills its last place, preserve the accepted joining request, apply Q9 to the joining student's other pending requests, and system-cancel other students' remaining pending applications/invitations to that group with reason "Group full". Preserve all completed outcomes and other students' requests to other groups.

- **Q57 — Private request reading:** Applications are readable only by applicant and target group manager; invitations only by sending manager and recipient. Other members, classmates, and teachers cannot read them.

- **Q58 — Pre-deadline group discovery:** Activity participants may inspect any group's current members and manager identity before the deadline. After the deadline, Q53 permits own final-group membership details and course peer profiles, without other groups' final memberships.

- **Q59 — No activity deletion, replacement, or reset:** V1 provides none of these operations, including as a way to correct an already-created capacity layout. Permitted instructions edits and deadline extensions retain their existing rules; recovery retries preserve the original allocation plan.

- **Q60 — Existing screens for status:** Users check application, invitation, and result screens by revisiting or refreshing. V1 has no email, push notifications, or separate notification center; existing status views and action confirmations remain required.

- **Q61 — Instructions remain editable after deadline:** The assigned teacher may edit the activity's task description and guidance after the grouping deadline, including during allocation and after roster finalization, subject to authentication, course assignment, resource authorization, and CSRF checks. The edit changes instruction text only; it cannot change capacities, participants, memberships, request outcomes, the allocation plan, the deadline, or CSV data, and does not reopen grouping. Confirmed saving shows the current saved text; rejected or definitively failed saves leave it unchanged. Q60's notification policy still applies; Q63 excludes user-facing description history and restore.

- **Q62 — Required plain-text description:** Activity instructions are required plain text, limited to 5,000 characters. Blank/whitespace-only or oversized descriptions are rejected with correction feedback at creation and on every subsequent edit; an invalid edit preserves the saved text. V1 introduces no rich-text editor, attachments, or additional activity fields.

- **Q63 — Current description only:** Users see only the latest saved activity description. V1 provides no user-facing description version history or restore feature; this does not remove the separate request history confirmed under Q67.

- **Q64 — Declared deadline timezone:** All deadline entry and display use America/New_York time, clearly labeled, regardless of the user's device timezone. Backend time remains authoritative and Q54/Q55 eligibility rules still apply.

- **Q65 — No earlier request expiry:** Pending applications and invitations have no separate expiration timer before the activity's current deadline. They end through a permitted response, Q9 join-triggered cancellation, Q56 group-full cancellation, or Q40 deadline expiry; an unanswered request may remain pending until one of these outcomes.

- **Q66 — No aggregate pending-request cap:** V1 adds no overall numeric limit to a student's pending applications or received invitations across groups. Q11/Q16 still enforce one pending application and one pending invitation per student/group pair; eligibility, full-group checks, and the deadline remain mandatory.

- **Q67 — Completed request history:** The two authorized parties may read every completed application or invitation attempt, with its outcome and cancellation reason, alongside subsequent attempts and after the deadline/finalization. Applications remain private to applicant and target manager; invitations remain private to sending manager and recipient. Preserve each completed attempt rather than replacing its visible outcome with a new pending attempt. History does not expose another student's final group or another group's final member list; it does not permit responding to a completed request or acting after the cutoff.

- **Q68 — Empty-group joining conflict:** If another student joins the empty group first, the losing request establishes no membership or manager status and triggers no join cleanup. Show "This group is no longer empty" with refreshed permitted group information; the student deliberately chooses whether to apply if still eligible, the group has capacity, and the deadline is still open. No application is automatically submitted.

- **Q69 — Unavailable final results:** After the deadline and before successful complete finalization, existing result screens explain that results are being finalized or temporarily unavailable, with a refresh option. Do not expose a partial roster or teacher grouping-progress counts. Refresh checks permitted status only; it does not restart allocation, choose a new plan, or resubmit a grouping mutation. Q44's preserved-plan recovery rules remain unchanged; there are no new teacher assignment or allocation controls.

- **Q70 — Seeded username/password login, no recovery UI:** Teachers and students log in with a provided username and password. Credentials are supplied through demo setup; V1 provides no self-service Forgot password workflow. Forgotten credentials require help outside the app, without introducing a registration or administrator workflow. Existing password hashing, authenticated sessions, authorization, and logout requirements remain in force.

- **Q71 — Applications require an occupied group and manager:** An ungrouped participant may apply only to an occupied group with a manager and available capacity before the current deadline. Applications to empty groups are rejected without recording a pending request; empty groups use deliberate first joining under Q28, which establishes the initial manager. Reapplications use the same checks.

- **Q72 — Minimum participants and nonempty capacity list:** Activity creation requires at least two eligible participants and a nonempty manually entered capacity list. Reject courses with zero or one participant and empty capacity lists with an explanation, leaving the course without a grouping activity. Each capacity must still be a whole number of at least 2, and the total must exactly equal the fixed all-enrolled participant count.

## 2. Product purpose and expected outcome

The system provides a classroom grouping platform that reduces opaque grouping information and reliance on private coordination. Students discover classmates and groups and manage team formation themselves. Teachers configure grouping activities for assigned courses and obtain final grouping results. The system handles remaining ungrouped students using the Q43 random remaining-student method.

Confirmed V1 outcomes:

- Teachers and students use pre-provisioned accounts.
- Teachers select assigned existing courses and create/configure grouping activities where needed.
- Students inspect permitted grouping information, join empty system-created groups, apply to join groups, and invite classmates who can accept or reject.
- At the deadline, the system expires pending requests, assigns all remaining eligible students into existing empty places using Q43 random ordering, and finalizes the complete roster.
- Students view their own final group and members.
- Teachers view, obtain, and export the complete final grouping roster after successful deadline completion and system finalization under Q40.

Q40 requires a finalized roster to include every eligible student in exactly one group. Q41 fixes the activity's eligible participant list at creation, with total capacity equal to that count, so remaining empty places equal the remaining ungrouped students. Creation-time consistency and processing recovery still require decisions; failures cannot justify an incomplete roster labeled final. No numeric adoption or processing-latency target is confirmed.

## 3. Actors and permission boundaries

### Teacher

Confirmed permissions:

- Log in and see only courses assigned to that teacher.
- Select an assigned course needing a grouping activity.
- Create and configure a grouping activity, including group sizes/limits, deadline, task instructions, and related explanations.
- View, obtain, and export the final grouping roster, subject to resource authorization and final-result availability.

Confirmed restrictions:

- No course creation in the UI.
- No live grouping-progress monitoring.
- No manual assignment of students to groups.
- No review or approval of student join applications.

These restrictions apply on the backend as well as in the UI. A teacher role alone does not grant access to all courses or activities. No teacher finalization action is confirmed.

### Student

Confirmed capabilities:

- Log in with a pre-provisioned account; course access is checked against pre-provisioned enrollments.
- View permitted activity instructions, classmates, basic classmate information, grouping status, groups, remaining capacity, and their own status.
- Join an empty system-created group, apply to join a group, and invite a classmate, subject to confirmed eligibility rules.
- Accept or reject an invitation and receive confirmation of the recorded outcome.
- View their own final group and permitted member information.

Only the group manager may approve or reject join applications and send invitations for that group. Q57 restricts request reading to the request's two parties; Q58 permits pre-deadline member/manager discovery; Q59 excludes activity reset. No additional management capability has been confirmed beyond the permissions stated here. No broader student access to the full final roster is assumed.

### System

The system authenticates users, enforces authorization and grouping rules, persists related changes, exposes permitted information, and handles remaining ungrouped students. Q40 confirms deadline-triggered automatic complete assignment and finalization; Q43 confirms shuffling the remaining students and filling existing empty places; execution/recovery details remain unresolved. No human assignment-review step or new administrator actor is introduced.

## 4. V1 scope and pre-provisioned data

### Confirmed demo model

Before demo use, appropriate seed data will pre-provision the database with:

- Teacher accounts and student accounts, including their respective roles.
- Courses.
- Teacher-course assignments.
- Student-course enrollments.

Teachers do not create courses in the UI. Course selection does not create a grouping activity automatically, and not every course needs a grouping activity. Each course may have at most one grouping activity in V1. Course enrollment at activity creation determines inclusion under Q42. Every then-enrolled student becomes a participant; Q51/Q53/Q57/Q58/Q67 govern visible fields and request/group access, subject to backend resource authorization.

Seeding is a setup responsibility, not a new runtime service or user-facing management workflow. This PRD does not specify seed records, credentials, scripts, or a database schema.

### Confirmed activity participant boundary

Q41 fixes the eligible participant list when activity creation succeeds; Q42 includes every student then enrolled in the course. Capacity validation, permitted student grouping actions, and complete deadline allocation refer to this same list and count. Later course enrollment changes do not automatically change this activity's participants. Q42 fixes initial inclusion as all students enrolled in the course at creation; creation-time consistency enforcement remains technical design work; authentication and resource/privacy checks still apply independently.

### Confirmed membership boundary

A student may belong to at most one group at a time within a grouping activity. Every operation that establishes membership, including deliberate first joining of an empty system-created group, joining through an application, accepting an invitation, and system assignment, must preserve this limit. Pending applications and invitations are not memberships. Only ungrouped students may join empty groups, submit applications, or receive new invitations, and joining cancels their other pending requests. Voluntary leaving and self-service switching are excluded under Q23; activity reset is excluded under Q59; per-action eligibility and lifecycle follow the confirmed rules; initial participation is fixed by Q42.

**Acceptance checks:** Concurrent attempts to establish membership in different groups cannot leave the student in both groups within the same activity. Application, invitation, and allocation outcomes must agree with the resulting membership. Having no membership means ungrouped; Q40 requires every eligible student to have exactly one group before finalization. A missing membership in a finalized result is an integrity failure.

### Confirmed pending-request cleanup

**Confirmed review decisions Q54–Q60:** Creation requires a deadline strictly in the future according to backend time; missing or nonfuture deadlines are rejected with correction feedback. Every student grouping mutation must pass its final backend eligibility check immediately before saving strictly before the current deadline, even if submitted earlier; there is no grace period. New applications and invitations to full groups are rejected. When a join fills the last place, preserve the accepted joining request and apply Q9 to the joining student's other pending requests; automatically system-cancel other students' still-pending applications and invitations targeting that group with the reason "Group full", leaving their requests to other groups and all completed outcomes unchanged. Capacity, pending-request state, membership uniqueness, and the cutoff must remain consistent under concurrent operations; the synchronization mechanism remains to be designed.

Applications are readable only by their applicant and the target group's manager; invitations only by their sending manager and recipient. Other members, classmates, and teachers cannot read those requests, including through direct access. Before the deadline, activity participants may inspect any group's current members and manager identity using the permitted profile fields; after the deadline Q53 restricts final membership information to a student's own group while course peer profiles remain readable. V1 provides no activity deletion, replacement, or reset. Users check existing application, invitation, and result screens by revisiting or refreshing; V1 has no email, push notifications, or separate notification center. Existing status views and action confirmations remain required.

**Q61 — Instructions remain editable after deadline:** The assigned teacher may edit the activity's task description and guidance after the grouping deadline, including during allocation and after roster finalization, subject to authentication, course assignment, resource authorization, and CSRF checks. The edit changes instruction text only; it cannot change capacities, participants, memberships, request outcomes, the allocation plan, the deadline, or CSV data, and does not reopen grouping. Confirmed saving shows the current saved text; rejected or definitively failed saves leave it unchanged. Q60's notification policy still applies; Q63 excludes user-facing description history and restore.

Every operation establishing membership also cancels the student's other pending join applications and received invitations in that activity. These related changes are recorded together transactionally; a request used to establish membership is accepted, while other pending requests are system-canceled rather than rejected by a person. This rule does not authorize users to withdraw or cancel requests manually, and it does not grant new visibility or notification permissions.

**Acceptance checks:** Empty-group joining, approved joining, accepted invitations, and system assignment all apply cleanup. Canceled requests cannot later establish membership through stale acceptance or approval requests. Previously accepted/rejected outcomes, requests in other activities, and invitations sent by the student to others are unaffected by this cleanup. An operation that does not establish membership performs no join-triggered cancellation; a definitive transaction failure commits neither the new membership nor its cleanup. If the response is lost, status checks reflect the actual committed outcomes.

### Confirmed application/invitation coexistence

An ungrouped student may have both a pending application and a pending invitation for the same group, with at most one pending request of each type. Recording one does not automatically approve, accept, or close the other, nor establish membership. Each keeps its existing decision-maker: the manager responds to applications and the recipient responds to invitations. Successful joining through one accepts the request used and cancels the still-pending counterpart under Q9, together with other pending requests in the activity. Neither reserves a place.

**Acceptance checks:** A pending application alone cannot block an otherwise permitted invitation for the same group/student pair, and a pending invitation alone cannot block an otherwise permitted application. The separate duplicate checks and eligibility rules still apply. Concurrent approval and acceptance cannot establish two memberships or bypass cleanup. Rejecting an invitation leaves the pending application unchanged and available for manager approval; rejecting an application leaves the pending invitation unchanged and available for recipient acceptance. Current joining conditions still apply. Rejection does not cancel other pending requests or create a standing ban on the pairing. Q9 cleanup occurs only when membership is established and affects only still-pending requests; completed rejections are preserved.

### Confirmed deadline expiration and completion

Q40 distinguishes expired requests from person-rejected or membership-canceled requests. At the current deadline, still-pending requests expire before remaining-student allocation; later system assignment does not rewrite expired outcomes. The system finalizes and exposes the teacher's roster only after every eligible participant belongs to exactly one group. This is availability for authorized viewing/export, not a push delivery requirement.

### Confirmed student grouping cutoff

All student grouping mutations are permitted only before the current activity deadline under Q39, subject to existing permissions and eligibility. After any permitted extension, the updated deadline governs the cutoff. This includes both accepting and rejecting applications/invitations, not only membership-establishing actions. A late student request cannot create, answer, or change a request or establish membership, even through direct access or stale UI. Q40 separately requires pending requests to expire, remaining eligible students to be allocated, and the complete roster to be finalized after the cutoff. Q53 permits reading course peer profiles after the deadline, while final membership details remain restricted to the student's own group. Reading permissions are confirmed; processing/recovery implementation remains technical design work.

**Profile-edit boundary (Q52):** The grouping cutoff does not disable a student's own course About me editor. Profile edits remain allowed after the deadline and finalization under existing ownership, access, and length checks; grouping actions and finalized membership remain governed by Q39–Q45. This grants no additional classmate discovery or full-roster permission.

### Confirmed group names

Q47 assigns fixed activity-local names in capacity-list order: Group 1, Group 2, and so on. Both student and teacher flows use these same names; no custom naming or renaming is available in V1. The name and capacity associated with each group remain stable as students join or are allocated.

### Confirmed group-size meaning and system-created groups

The teacher manually enters a capacity layout: one capacity for each system-created group. For example, 2, 2, 2, 3 specifies four groups for the confirmed nine-student example. Each capacity is its group's joining limit. A normal target size describes the teacher's intention but is not the layout-generation input; no automatic remainder calculation is selected.

The system creates the groups before student participation. The capacity layout is fixed after creation under Q34. Students cannot create groups; the first deliberate empty-group join establishes the initial manager. Applications to occupied groups require manager approval, and invitations retain their existing acceptance route. The exact-total rule and whole-number minimum of 2 are confirmed under Q30/Q31; no additional size limit is selected; creation-time consistency enforcement remains technical design work; Q46 confirms no manager for system-only groups. System assignment must respect the prepared capacities; insufficient capacity is an integrity failure, so Q69 shows unavailable results rather than a partial final roster; recovery implementation remains technical design work, with no overflow or extra-group creation.

### Confirmed exclusions

- Self-registration and course enrollment management.
- Teacher course creation in the UI.
- Student group creation.
- Custom group naming or renaming by teachers or students.
- Changes to the group-capacity layout after activity creation.
- Changes to the activity participant list after creation.
- Teacher selection of a participant subset or exclusion of individual enrolled students.
- Student access to the teacher's full final grouping roster.
- Shortening an activity's deadline.
- Deadline extension or reopening after the current deadline passes.
- Multiple grouping activities for the same course.
- Student manual withdrawal of pending join applications.
- Manager manual cancellation of pending invitations.
- Voluntary group departure by ordinary members or managers, including a self-service switch that leaves an existing group.
- Removal of existing group members by the manager.
- Transfer or delegation of the manager's application-decision and invitation authority.
- In-app candidate suggestions and messaging integration for recommendations to the manager.
- Teacher live-progress monitoring, manual student assignment, and application review/approval.
- AI grouping assistance, AI teammate recommendations, AI allocation, and AI infrastructure.

Q70 excludes self-service password recovery; forgotten credentials require help outside the app. Q33 excludes a saved draft/publish lifecycle and scheduled opening from V1; Q59 excludes deletion/replacement/reset; Q60 resolves notification scope; the confirmed lifecycle rules govern V1. Manual applicant withdrawal and manager invitation cancellation are excluded under Q12 and Q21; automatic cancellation upon membership is confirmed under Q9.

### Manager permission boundary

The initial group manager alone may approve or reject pending applications and send invitations; this authority cannot be transferred or delegated in V1. The manager may not remove an existing member. Direct removal or authority-transfer requests leave membership, group capacity, and decision authority unchanged. These restrictions include groups with system-assigned members. Declining a pending application affects that request only; it cannot remove an established membership or cancel a separate pending invitation under Q20. Activity reset is excluded under Q59 and is not granted by manager status.

Ordinary members may identify candidates through the permitted discovery information in FR-B0 and recommend them to the manager in person or through an existing communication channel. V1 provides no in-app suggestion submission, suggestion approval, chat, or messaging integration for this coordination. The manager still decides whether to send an invitation, and current eligibility checks apply; a recommendation itself changes no request or membership state.

**System-only groups (Q46):** Groups first populated by system assignment after the deadline have no manager. Existing managers are retained in other groups. Manager absence is valid and cannot prevent complete allocation, finalization, or member result access; Q39 blocks all student grouping actions after the deadline regardless of manager status.

### Prioritized V2 follow-up

Voluntary group departure is a standard capability deferred temporarily from V1 and prioritized for V2 by the product owner. Its detailed rules, including manager succession, empty groups, timing relative to deadlines/finalization, and effects on pending requests, require separate V2 decisions. This records a priority without a delivery date or implementation commitment. AI remains outside V1; this priority does not select or implement future AI functionality.

## 5. Functional requirements and nine-flow coverage

Shared security and failure requirements in Sections 6–7 apply to every relevant flow. Each requirement ID maps directly to the identically labeled flow in [User Flows](user-flows.md).

### FR-A0 — Teacher logs in and selects an assigned existing course

**Status:** Confirmed. **Actor:** Teacher. **Trigger:** The teacher wants to select a course for a grouping activity.

1. The teacher logs in with a pre-provisioned account, unless already authenticated with a valid teacher session.
2. The backend authenticates the account, verifies the teacher role, and retrieves only that teacher's assigned courses.
3. The system displays the assigned existing courses.
4. The teacher may select a course needing an activity or stop without creating anything.
5. The backend rechecks session, role, and the selected course assignment before granting course access and continuing to FR-A.

**Acceptance checks:** An authenticated teacher sees only assigned courses; requesting an unassigned course directly does not grant access. No assigned courses produces a no-courses outcome without a course-creation control. Selecting a course creates neither a course nor an activity.

**Failure/alternative outcomes:** Invalid login, expired session, denied access, retrieval failure, no assigned courses, or no selection.

**Confirmed:** Provisioning and course selection are settled; deferred technical design is identified in Section 9.

### FR-A — Teacher creates a grouping activity

**Status:** Confirmed product behavior; implementation details remain technical design work. **Actor:** Teacher. **Trigger:** Activity creation for a selected assigned course.

1. The teacher requests the creation form; the backend checks the teacher session and course assignment.
2. The teacher enters required plain-text instructions (nonblank, at most 5,000 characters), the manual capacity list, and a future deadline in labeled America/New_York time, then submits.
3. The backend rechecks authentication, assignment, authorization, CSRF, the one-activity-per-course limit, at least two eligible participants, a nonempty capacity list, the required plain-text instructions and 5,000-character limit, that the deadline is present and strictly in the future according to backend time, that each submitted capacity is a whole number of at least 2 and their sum equals the eligible student count, and the remaining activity validation rules. A second activity for the same course is rejected.
4. Invalid inputs, including fewer than two participants, an empty capacity list, missing/nonfuture deadlines, entries that are not whole numbers of at least 2 or a total below or above the eligible student count, produce correction feedback; the teacher may correct and resubmit or stop.
5. A permitted submission fixes the eligible participant list and saves it with the activity and all its groups together transactionally, with one group per teacher-entered capacity, named Group 1, Group 2, and so on in list order under Q47. The teacher sees activity details after confirmed creation; there is no separate group-generation step. The activity is immediately visible to eligible students and open for permitted grouping actions after successful creation under Q33; no separate publish step or scheduled opening is provided.

**Acceptance checks:** Valid, authorized inputs under the confirmed rules create an activity and its complete set of groups for the selected course if it has none. A definitive transaction failure saves neither the activity nor any of its groups; a lost response requires checking actual state rather than assuming failure or creating duplicates. Requests to create a second activity are rejected without changing the existing activity; concurrent creation requests cannot result in multiple activities for one course. Input failures do not show creation success. Security failures stop the request rather than entering an input-correction loop. Activity details do not expose live grouping progress.

**Failure outcomes:** Access/security rejection, invalid input (including blank/oversized instructions or a missing/nonfuture deadline), save failure, or uncertain submission outcome.

**Technical design remaining:** Creation-time consistency enforcement and deadline/concurrency implementation. Q72 requires at least two participants and a nonempty list; Q30/Q31 enforce exact total and whole-number capacities of at least 2. Q64 fixes America/New_York entry/display. Q34/Q59 prohibit capacity edits and deletion/replacement/reset; Q35/Q61 permit text-only instructions edits and Q36–Q38 govern deadline amendments. Additional settings or limits have not been selected.

**Instructions amendment (Q35, Q61):** The assigned teacher may submit revised task-description text while grouping is open, after the deadline, during allocation, and after finalization. The backend rechecks authentication, course assignment, resource access, CSRF, and the required nonblank plain-text description of at most 5,000 characters before saving; the grouping cutoff does not block this text-only edit. Confirmed success shows the saved text; rejected or definitively failed edits leave the saved instructions unchanged. An instruction edit cannot alter the deadline, capacity layout, groups, memberships, or pending requests. Q60 confirms existing screens without email, push, or a separate notification center; Q63 excludes user-facing description history and restore; this capability grants no teacher live-progress access.

**Deadline extension (Q36–Q38):** While grouping is still open, the assigned teacher may submit a new deadline later than the current one. The backend rechecks authentication, assignment, resource access, CSRF, current open status, and the current stored deadline before saving. Earlier deadlines are rejected under Q37; extensions and reopening after the current deadline passes are rejected under Q38. Confirmed success shows the saved deadline; rejected or definitively failed requests preserve the prior deadline and all other activity/group/request state. No teacher live-progress access is granted. Q55 fixes the backend-time cutoff rule; concurrency implementation remains technical design work; no deadline-history feature has been selected; Q60 resolves notification scope; Q40 confirms automatic allocation and system finalization after the deadline; execution/recovery details remain open.

### FR-B0 — Student discovers classmates and groups

**Status:** Discovery, profile editing, and reading permissions confirmed. **Actor:** Student. **Trigger:** Browsing an accessible activity to find teammates or inspect grouping information.

1. The student requests discovery information for an activity in an enrolled course.
2. The backend verifies the student session, enrollment, resource access, activity participation where applicable, and confirmed visibility rules.
3. The system returns permitted instructions, the classmate list and grouping statuses, groups and remaining capacity, and the student's own status.
4. The student may request a permitted classmate profile showing Student name, institutional Student ID, and that course's optional About me under Q51, subject to resource checks.
5. Before the deadline, activity participants may inspect any group's current members and manager identity under Q58, using permitted name, institutional Student ID, and About me fields.
6. Discovery may lead to FR-B1, FR-B2, or FR-B3; browsing does not require a grouping mutation.

**Acceptance checks:** All confirmed discovery information is represented, limited by approved visibility rules. Denied details are not disclosed. Displayed capacity is informational and does not reserve a place. No teacher discovery/live-progress view is provided.

**Failure outcomes:** Missing/expired session, denied access, restricted information, or retrieval failure.

**Confirmed visibility:** Q51 specifies the three basic profile fields; Q53 fixes post-deadline membership privacy, Q58 permits pre-deadline member/manager reading, and Q57/Q67 restrict requests and history to their two parties. No additional profile field or management capability is required by the confirmed scope.

**Post-deadline discovery (Q53):** Course peers can still read names, institutional Student IDs, and current About me. Peer-profile requests cannot reveal another group's final membership list or another student's final assignment, including through direct requests. Own finalized-group/member access follows Q45; full-roster access stays teacher-only. Profile reads and own-introduction edits do not restore any grouping action after Q39's cutoff.

**Own About me editing (Q51–Q52):** A student may edit their own introduction in the course context. The backend verifies authentication, student identity, course/resource access, ownership of the introduction, CSRF, and applicable editing availability; it accepts plain text of 0–500 characters and rejects longer text with correction feedback. Confirmed saving changes only that student's About me for that course. Other students/teachers cannot use this action to edit it; changes do not modify seeded names/IDs, grouping state, capacities, participants, requests, allocation plans, or CSV columns. Failed/denied saves leave the prior introduction unchanged. Q52 permits this edit after the grouping deadline and finalization; character-counting implementation remains technical design work; confirmed ownership and resource authorization rules apply.

### FR-B1 — Student deliberately joins an empty system-created group

**Status:** Confirmed under Q28/Q68; deadline and concurrency implementation remain technical design work. **Actor:** Ungrouped student. **Trigger:** Choosing to join an empty group within an accessible activity.

1. The student views the pre-created groups and their predetermined capacities, then deliberately chooses to join an empty group, with the initial manager role made clear.
2. The backend checks authentication, student role, enrollment, resource access, CSRF, current activity eligibility, that the student remains ungrouped, and that the target group is still empty with available capacity.
3. A permitted join establishes membership, the initial group manager identity, and Q9 cancellation of the student's other pending requests together transactionally. The student counts toward the group's capacity.
4. The system shows confirmed membership and manager status. Students cannot create additional groups.

**Acceptance checks:** Concurrent first-join attempts cannot establish more than one initial manager or bypass approval for an occupied group. If the group becomes occupied before a student's join commits, the request cannot directly add that student; they must use an approved occupied-group route. Q68 requires occupied-group feedback and refreshed permitted information; only the student may choose a separate application if eligible, with no automatic submission. Concurrent joins to different groups cannot establish two memberships. A definitive transaction failure establishes neither membership, manager identity, nor cleanup; the pre-created group remains. After a lost response, current state determines whether the join committed.

**Membership boundary:** Already-grouped students cannot join an empty group, including through stale UI or direct requests. Existing membership remains unchanged; no voluntary departure, switch, removal, or transfer is implied. The initial manager alone decides applications and sends invitations, cannot remove members, and cannot transfer or delegate authority.

**Failure outcomes:** Denied access, changed group occupancy or student eligibility, unavailable capacity, processing failure, or uncertain outcome.

**Technical design remaining:** Deadline synchronization and concurrency implementation. Q68 settles first-join conflict feedback; fixed group names, capacity/availability, current members/manager before the deadline, and own final-group access use the confirmed fields and permissions. Additional display fields or management capabilities have not been selected. Q47 confirms fixed names in capacity-list order; Q46 excludes manager appointment through post-deadline system assignment.

### FR-B2 — Student applies to join a group

**Status:** Eligibility, manager-reviewed applications, request lifecycle, and private history confirmed. **Actors:** Applying student; target group manager.

1. The student inspects an occupied group with a manager and displayed availability, then submits an application. Empty groups use FR-B1; an application to an empty group is rejected without recording a request.
2. The backend checks security, inputs, and current application eligibility, including that the student has no membership in this activity and the group is occupied with a manager and is not full, then records a permitted application as pending and shows its status. Submitting an application neither joins the group nor reserves a place. Already-grouped students cannot submit an application or reapplication in that activity.
3. The target group's manager opens a pending application and submits acceptance or rejection. The application remains pending until a permitted response, Q9 join-triggered cancellation, Q56 group-full cancellation, or Q40 deadline expiry. The backend validates that the reviewer is the target group's manager, has current resource access, and passes CSRF and current application-state checks. Other members cannot approve, reject, or read these applications under Q57. Teachers cannot review or decide the application.
4. A permitted rejection records and confirms rejection without establishing membership or changing a pending invitation for the same student and group. A permitted acceptance proceeds to a fresh eligibility/capacity check, including membership uniqueness.
5. When joining is allowed, membership, the accepted application outcome, and Q9 cancellation of the student's other pending requests and Q56 cancellation of other students' pending requests to the group if its last place is filled are recorded together transactionally and confirmed to the applying student.

**Concurrent applications:** An ungrouped student may have applications pending with multiple different groups in the same activity. An existing pending application to one group is not, by itself, grounds to refuse an application to a different group. For any one student and target group, at most one application may be pending; repeated or concurrent submissions cannot create duplicates. Reapplication after rejection is permitted. Other eligibility rules remain applicable; Q66 confirms no aggregate pending-request cap; Q67 preserves completed attempt history and all new attempts require current eligibility.

**Duplicate checks:** A second submission for the same student and group while an application is pending does not create another pending application, including concurrent submissions. Applications to different groups remain permitted under Q10. Reapplication after rejection is permitted under Q13; completed outcomes do not bypass current eligibility; Q67 preserves each completed attempt's history.

**Reapplication after rejection:** A student may submit another application to the same group after rejection when current eligibility permits. The permitted attempt becomes pending, requires manager approval again, and reserves no place. Concurrent resubmissions still produce at most one pending application. A prior rejection alone cannot prohibit the new attempt. Q67 requires readable history of every completed attempt, with outcomes and cancellation reasons preserved alongside new requests; storage design remains a technical decision.

**Applicant eligibility checks:** A student already belonging to a group in this activity cannot submit an application to that or another group, including through a direct request or stale UI. Rejected submission records no new pending application and changes no existing membership. The rule is enforced against current state, including concurrent submission and joining; any permitted recorded request is canceled if joining then occurs under Q9. Application submission does not authorize leaving or switching.

**No applicant withdrawal:** Students cannot manually withdraw pending applications in V1. The UI offers no withdrawal action, and direct withdrawal requests cannot change application state. Students wait for the application outcome; joining through another route still automatically cancels other pending requests under Q9. Q65 supplies no earlier expiration timer; Q9/Q56/Q40 define automatic request closure.

**Acceptance checks:** Application submission results in a pending application without membership, reserved places, or reduced available capacity. Another permitted join may use that capacity before approval; if the group has filled, approval cannot establish membership or exceed capacity. Joining through an application requires approval by the target group's manager; pending or rejected applications do not claim confirmed joining. Other group members, managers of other groups, applicants, and teachers cannot approve or reject this application's outcome, including through direct requests. Approval cannot bypass current eligibility, membership uniqueness, or capacity checks, even when those conditions were satisfied at submission. The accepted outcome and membership agree after transactional recording; if joining is blocked, the system does not claim accepted membership. A filled group system-cancels its remaining pending requests under Q56; joining elsewhere triggers Q9 and the deadline triggers Q40. An access/security or integrity failure does not establish membership; the request's actual persisted state must be checked before retrying.

**Failure outcomes:** Security/input/eligibility rejection, changed group or application state, insufficient capacity under approved rules, processing failure, or uncertain outcome.

**Technical design remaining:** Persistence and concurrency implementation preserving Q71 application eligibility, Q9/Q56/Q40 terminal outcomes, per-pair uniqueness, and Q57/Q67 private history. Q65 excludes earlier expiry; Q66 excludes aggregate caps.

### FR-B3 — Student invites a classmate and receives a response

**Status:** Manager-only sending, recipient eligibility/responses, request lifecycle, and private history confirmed. **Actors:** Target group manager (sender) and invited student (recipient). **Trigger:** The manager selects an ungrouped classmate to invite; the recipient later opens the invitation.

1. The group manager selects their group and a classmate and submits an invitation.
2. The backend validates security, inputs, that the sender is the target group's manager with current resource access, that the recipient has no membership in this activity, that the group is not full, and the current activity deadline. It records a permitted invitation and makes it available to the recipient. Other members and teachers cannot send invitations for that group; already-grouped classmates cannot receive one for this activity.
3. The recipient opens the invitation; the backend checks identity and access before showing permitted invitation and group information.
4. The recipient submits acceptance or rejection; the backend validates security and current invitation state.
5. A permitted rejection is recorded and confirmed to the recipient without changing a pending application for the same student and group. Pending invitations do not reserve places. Acceptance requires fresh joining eligibility/capacity checks before membership, the accepted outcome, and Q9 cancellation of the recipient's other pending requests and Q56 cancellation of other students' pending requests to the group if its last place is filled are recorded together transactionally and confirmed to the recipient. If the group has filled in the meantime, acceptance does not establish membership or exceed capacity.
6. Independently, the sender may later request invitation status. The backend rechecks access and shows the permitted recorded response or current lifecycle status if no response has been recorded.

**Acceptance checks:** Only the target group's manager may send an invitation; direct requests by other members, managers of other groups, or teachers are rejected without recording an invitation. Sending an invitation neither establishes membership nor reserves or reduces available group capacity. Another permitted join can use that capacity before the recipient responds; later acceptance is blocked if the group is full, without claiming membership success. Both acceptance and rejection receive recipient confirmation after successful recording. Recipient response processing does not depend on the sender returning. A sender lookup does not claim a response exists when none has been recorded. Pending invitations do not imply membership.

**Recipient eligibility checks:** Sending an invitation to someone already grouped in this activity records no new invitation and changes no membership, including through direct requests or stale UI. The backend enforces current membership status under concurrent invitation sending and joining; if the invitation is recorded first, subsequent joining cancels it under Q9. If membership is already established, the invitation cannot be recorded afterward. Acceptance cannot bypass cancellation or membership uniqueness and cannot transfer the recipient out of another group.

**Duplicate invitation checks:** If a pending invitation already exists for this group and recipient, another send cannot create a duplicate, including concurrent sends. Reinvitation after rejection is allowed under Q17; Completed outcomes do not bypass current eligibility; Q67 preserves their history. Pending invitations from different groups may coexist under Q18.

**Reinvitation after rejection:** A group manager may send another invitation to a student who previously rejected an invitation from that group, provided the recipient is currently ungrouped and other eligibility checks pass. Prior rejection alone does not prevent sending; concurrent sends still produce at most one pending invitation for that group and recipient. The permitted invitation awaits the recipient's response, reserves no place, and requires current joining checks when accepted. Q67 requires retaining every completed attempt's visible outcome alongside subsequent attempts; storage design remains a technical decision.

**Invitations from different groups:** An ungrouped student may have invitations pending from multiple different groups in the same activity. A pending invitation from one group alone does not block sending from a different group. Each group/recipient pair still has at most one pending invitation, and no invitation reserves a place. Joining cancels other pending requests under Q9; concurrent acceptances cannot establish membership in multiple groups. Q66 confirms no aggregate pending-request cap. An application and invitation for the same pair may coexist under Q19.

**No manager cancellation:** Group managers cannot manually cancel invitations while they are pending. The UI provides no cancellation action, and direct cancellation requests cannot change invitation state. Recipients can still respond subject to current invitation, eligibility, and capacity checks. Join-triggered automatic cancellation remains applicable; this rule does not guarantee a response or choose expiration or deadline behavior.

**Failure outcomes:** Disallowed invitation/response, denied access, changed eligibility/capacity, processing failure, or uncertain outcome.

**Technical design remaining:** Persistence and concurrency implementation preserving manager-only sending, ungrouped recipients, available capacity, current deadline, pending-state checks, Q9/Q56/Q40 terminal outcomes, per-pair uniqueness, and Q57/Q67 private history. Q65 excludes earlier expiry; Q66 excludes aggregate caps.

### FR-C — System expires requests, assigns remaining students, and finalizes

**Status:** Deadline-triggered automatic completion confirmed under Q40; random assignment method confirmed under Q43; execution/recovery details unresolved. **Actor:** System. **Trigger:** The activity's current deadline.

1. Stop student grouping mutations under Q39 and expire every still-pending application/invitation in this activity. Preserve all completed outcomes; expiration is not a rejection or a membership-triggered cancellation.
2. Identify the activity's eligible students, established memberships, and remaining places in the existing groups.
3. On the initial run, randomly shuffle the remaining eligible ungrouped students and choose their placements into existing empty places under Q43. Retain that allocation plan before applying assignments; any retry uses the original plan under Q44. Past applications and invitations, including rejected or expired ones, do not guide allocation. Established memberships remain unchanged; assignment needs no application, invitation, or manager approval.
4. Preserve existing managers and leave groups first populated by system assignment managerless under Q46. Reconcile the plan with actual saved memberships and record missing assignments transactionally without duplicates or relocating saved members, enforcing one membership per student and each fixed group capacity. Q9 cleanup affects only requests still pending; a request already expired under Q40 retains its expired outcome.
5. Verify that every eligible student has exactly one membership and no group exceeds capacity. After complete successful processing, the system finalizes the roster and makes the complete roster available to the assigned teacher and each participant's own final group/members available to that participant under Q45. If no students need assignment, deadline expiry and completion validation still occur before finalization.
6. While complete finalization is pending or unavailable, existing result screens show Q69's finalizing/unavailable message and a read-only refresh option, without a partial roster or teacher progress counts. If processing fails, is incomplete, or has an uncertain outcome, reconcile actual persisted state and do not expose an incomplete roster as final. Q44 requires resuming the same plan, preserving committed memberships and avoiding duplicates. Retry trigger/backoff, recovery implementation, and transaction scope remain unresolved.

**Acceptance checks:** Completion uses no AI, new groups, overflow, relocation of existing members, or teacher manual assignment/approval. No final roster is available before the current deadline and successful completion. Once finalized, every eligible student is grouped; Q41 fixes the participant set, so exact total capacity means every place is filled after successful complete assignment. Earlier accepted/rejected/canceled outcomes and other activities are unchanged by expiration. Pre-deadline membership-triggered cancellation and deadline expiry keep distinct reasons.

**Retry acceptance example (Q44):** If the chosen plan is Amy → A, Ben → B, Chloe → C, and only Amy's membership commits before failure, a retry retains Amy → A and completes Ben → B and Chloe → C. It must not redraw Ben/Chloe's placements or create a duplicate membership. If the original completion succeeded but its response was lost, a retry must reconcile and preserve that completed roster rather than execute a new random allocation.

**Technical design remaining:** Creation-time consistency checks, slot enumeration, randomness implementation, and plan-retention design, mechanism and processing delay after the deadline, cutoff races, inconsistency/failure recovery, intermediate transaction scope, safe retries (Section 9). Intermediate processing must never be labeled a final incomplete result. The deadline trigger does not select a scheduler, queue, worker, or randomness implementation. Q60 excludes email, push, and a separate notification center.

### FR-D — Teacher views, obtains, and exports the final roster

**Status:** Complete final-roster access and CSV download confirmed under Q40/Q48; CSV rows and columns confirmed under Q49; remaining output behavior unresolved. **Actor:** Teacher. **Trigger:** Requesting an activity's final roster.

1. The teacher selects an activity and requests its final grouping roster.
2. The backend verifies teacher identity, course assignment, activity access, and availability after deadline expiry and complete successful Q40 processing.
3. If available, the system retrieves and displays the permitted complete final roster.
4. The teacher may request a CSV download of the finalized roster under Q48. The backend rechecks assigned-course access and successful finalization, prepares one row per participant with Group name, institutional Student ID, and Student name from that final result under Q49, sorts rows by numeric group number then Student ID ascending under Q50, and delivers the file.
5. If unavailable, the system reports that outcome without revealing live/in-progress grouping data or offering manual assignment.

**Acceptance checks:** Authorized teachers can view, obtain, and export available final results. The CSV contains every fixed participant exactly once, in the confirmed three-column layout, with their finalized group and institutional student number/name; no participant is duplicated or omitted, including system-assigned members. Rows follow ascending group number and Student ID within each group, consistently across repeated downloads. Direct requests cannot bypass final-result availability or resource checks. Unavailable results do not expose live progress. Q40 requires system finalization after complete assignment; no teacher finalization step is provided.

**Failure outcomes:** Denied access, unavailable final roster, read failure, output preparation failure, or delivery failure.

**Technical design remaining:** Failure/retry handling and processing delay, CSV encoding/dialect, filename, output consistency (Section 9). Q40 confirms system finalization after the deadline and complete assignment. No file-storage service is implied.

### FR-E — Student views their final group and members

**Status:** Own final-group access at system finalization and confirmed profile fields/reading permissions. **Actor:** Student. **Trigger:** Returning to retrieve their final grouping result, including after system assignment.

1. The student requests their own final group for an activity.
2. The backend verifies student identity, enrollment, activity participant access, and successful complete system finalization under Q40/Q45.
3. The system retrieves the student's permitted result and shows the final group and permitted member information when membership exists.
4. The flow can succeed without the student having joined an empty group, applied, or accepted an invitation during that visit.
5. A finalized result must contain a group for each eligible participant under Q40; missing membership is a processing/data-integrity failure, not an allowed final result.

**Acceptance checks:** A permitted available result displays the student's own final group and members. Q45 grants no access to the full final roster; direct requests for that teacher resource must not disclose it to students. Unavailable, failed, or no-group outcomes do not claim confirmed membership.

**Failure/alternative outcomes:** Authentication required, denied access, result unavailable, retrieval failure, or missing-membership integrity failure.

**Technical design remaining:** Integrity/failure recovery implementation. Q51 profile fields and Q53/Q57/Q58/Q67 permissions govern member information; Q69 governs unavailable results.

## 6. Shared security and authorization requirements

All requirements in this section are confirmed architectural constraints.

- **SEC-01:** Use Spring Security local-account authentication and server-side sessions. Teachers/students submit the username and password provided by demo setup under Q70. Authenticate against stored account information and password hashes; no self-service Forgot password workflow is provided. Logout invalidates the authenticated session.
- **SEC-02:** Use Secure, HttpOnly session cookies. The browser holds an identifier; authenticated session state stays server-side. Concrete cookie settings and session persistence remain technical decisions.
- **SEC-03:** Preserve CSRF protection for state-changing browser requests. Failed checks stop the request. Same-origin deployment does not remove this requirement; ordinary reads do not require a state-changing CSRF check.
- **SEC-04:** Enforce roles and resource-level authorization on the backend, including course assignments/enrollments and relevant activity, group, application, and invitation access. Recheck relevant relationships on resource requests, not just filtered lists.
- **SEC-05:** Enforce teacher restrictions even for direct API requests. Frontend validation, hidden controls, and role labels alone are insufficient security boundaries.
- **SEC-06:** Missing/expired sessions require authentication; login failure never grants requested access. A security failure is not repaired by changing activity inputs.
- **SEC-07:** Only the backend connects to PostgreSQL over a protected connection. Database credentials must not be exposed to browsers or included in the React build.
- **SEC-08:** Deliberately configure cookie policies and any development-time CORS; deployed React and API traffic share one HTTPS origin.

**Request history reading (Q57, Q67):** Existing application/invitation screens let each authorized party inspect pending and completed attempts and their outcomes/cancellation reasons, including after resubmission and after the deadline/finalization. Backend actor/resource checks apply to every history read; teachers and other members cannot read it. Completed history does not reopen responses or disclose other students' final assignments. Refresh is a read and never automatically repeats a mutation.

**New attempts after completed outcomes:** Reapplication/reinvitation after rejection remains allowed under current checks. A student whose requests were canceled because they joined is already grouped and ineligible; a group whose requests were canceled as "Group full" still has no places; expired requests belong to a closed grouping period. These existing rules determine eligibility without introducing a blanket rejection/cancellation ban or changing historical outcomes.

## 7. Integrity, failure handling, and operational constraints

Confirmed requirements:

- **REL-01:** Persist durable application data in PostgreSQL, not the hosting application's temporary filesystem.
- **REL-02:** Coordinate related writes within database transactions and enforce relational integrity. Protect confirmed membership/capacity rules under concurrent changes; transactions alone do not enforce capacity.
- **REL-03:** Recheck current data when processing grouping changes. Previously displayed capacity is not authoritative for a later mutation.
- **REL-04:** Provide actionable input feedback and distinguish rejection, pending state, confirmed success, definitive failure, and uncertain outcome. Do not present rejected or failed operations as success.
- **REL-05:** After a lost response or timeout, support checking permitted current state before repeating a mutation because the original operation may already have committed. Retry and duplicate-request design remain open.
- **REL-06:** Backend/database unavailability, cold starts, and restarts may cause unavailable or uncertain outcomes. Do not claim successful retrieval or mutation without confirmation.
- **REL-07:** Preserve the confirmed maximum of one group membership per student per grouping activity under concurrent operations. Each predetermined group capacity must be respected; creation-time counting and the concurrency mechanism remain technical design work; no additional capacity limit is selected.
- **REL-08:** After the deadline, result screens show finalization in progress or temporary unavailability with a refresh option until complete finalization succeeds (Q69). They expose no partial roster or teacher progress counts; refresh never restarts allocation or chooses a different plan.

No uptime guarantee, load envelope, latency threshold, browser-support matrix, or backup target is established by the sources. Hosting suitability and technical resilience must be validated; this draft does not invent numerical requirements.

## 8. Architecture and deployment decision status

### Confirmed

- Modular monolith: one Spring Boot application and one PostgreSQL database, with logical internal modules rather than independently deployed services.
- Frontend: React + Vite + TypeScript. Vite builds the assets; it is not a separate production application server.
- Backend: Spring Boot + Java, serving both the React build and REST API under one HTTPS origin, including direct frontend-route navigation support.
- Authentication/security: Spring Security and server-side sessions under Section 6.
- Logical responsibilities: frontend presentation, build serving, security, REST controllers, application/domain services, persistence, and durable database storage. Course/activity management, student discovery/team formation, and results are internal functional responsibilities.
- No AI services or unnecessary additional infrastructure in V1. Future AI features and pgvector are possibilities, not V1 requirements or dependencies.

### Provisional hosting and required validation

Render Free + Neon Free is the provisional initial solution, with a proposed single Spring Boot instance. It is not yet validated for this application and does not establish suitability for reliable live classroom use.

During Project Foundation, before substantial feature development, perform an early deployment smoke test covering:

- Application startup and deployment readiness.
- Memory and CPU constraints.
- PostgreSQL connectivity and connection recovery.
- Session, cookie, and CSRF behavior.
- Cold starts and application restarts.
- Basic API responsiveness.

If Render Free is unsuitable, reevaluate the hosting provider while preserving the application architecture where practical. This PRD does not deploy services or assert that free-tier constraints have been satisfied.

### Unresolved technical decisions

- In-memory versus persistent sessions; in-memory sessions would be lost on application stops, restarts, or redeploys.
- Final hosting provider and compute plan after validation.
- Persistence library, relational schema, and migration approach.
- Transaction scope and concurrency control for grouping operations.
- JVM, connection-pool, timeout, and concrete cookie settings.
- Backup and recovery arrangements.
- Safe retries and duplicate-request behavior.

These are technical decisions, not permission changes or resolved business rules. Persistent sessions do not automatically imply a new service.

## 9. Deferred technical design

No product interview question remains identified after Q71/Q72. The user confirmed the shared V1 scope on 2026-10-12, completing product grilling. The decision ledger marks historical proposals superseded by later answers. Deferred technical work requires separate authorization before execution.

Additional profile fields, group-management rights, configuration history, messaging, or other features have not been selected; generic earlier placeholders do not add them to V1.

The following are technical design work for architecture/foundation, rather than additional product interview questions:

- Creation-time consistency of the fixed all-enrolled participant list, count, capacity validation, and transactional activity/group creation.
- Deadline-extension, student-action, expiry, and allocation synchronization under concurrency.
- Membership/capacity and pending-request uniqueness enforcement; safe duplicate handling and reconciliation after lost responses.
- Allocation slot enumeration, randomness implementation, single-plan retention, transaction scope, execution mechanism, retries, and failure/integrity recovery. Q44 preserves the original plan; Q69 fixes user-visible waiting/unavailable behavior.
- Request-history persistence and pagination, preserving every completed attempt and actor-only access under Q57/Q67.
- Text character-count implementation, rendering/escaping, and validation consistent with Q51/Q62.
- CSV encoding/dialect, filename, escaping, and coherent output from the complete finalized roster, preserving Q49/Q50 columns, coverage, and ordering.
- Section 8's hosting validation, session/cookie settings, persistence/migrations, and backup/recovery work.

Technical decisions must preserve all confirmed product rules and cannot add permissions, feature requirements, or V1 AI. Application implementation remains outside the current documentation task.

## 10. Confirmed scope and future validation

This document covers all nine source flows: A0, A, B0, B1, B2, B3, C, D, and E. Functional acceptance checks must be evaluated alongside the shared security, integrity, and failure requirements; happy-path checks alone are insufficient.

Product-scope review is complete. The following checks remain for future technical design and implementation validation:

- Verify the seeded account/course/assignment/enrollment model and assigned-only teacher access.
- Verify all confirmed student capabilities and final-result capabilities are retained.
- Verify every teacher restriction is enforced without exposing live progress through alternate results or allocation paths.
- Preserve the user's confirmed V1 scope and resolve deferred technical design before any separately authorized implementation.
- Validate provisional hosting during Project Foundation before substantial feature development.

This draft does not authorize application implementation, introduce a new workflow, assign policy defaults, add infrastructure, or select deferred technical decisions. Quantitative success metrics and any additional requirements require explicit agreement rather than inference from this document.
