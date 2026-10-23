# Classroom Grouping System — V2 PRD

## 1. Status, authority, and purpose

**Requirements confirmed on 2026-10-19.** The owner confirmed the consolidated scope at Q79 after decisions Q1–Q78. This document specifies V2; it does not authorize implementing a slice, selecting a provider, paid execution, provisioning, or deployment.

Read with [project-wide assumptions](project-assumptions.md), [V1 PRD](PRD.md), [V1 technical decisions](tech-stack.md), [V2 architecture](v2-architecture.md), [V2 flows](v2-user-flows.md), [V2 roadmap](v2-implementation-roadmap.md), and [glossary](../GLOSSARY.md). [Discovery decisions](v2-discovery.md) retain the interview record. This PRD consolidates and supersedes earlier V2 proposals; V1 behavior continues except where explicitly amended below. Historical verification reports are not current V2 acceptance.

V2 makes pre-cutoff group membership manageable, adds course announcements, and provides an optional course assistant grounded in permitted current course content. These are three independently gated workstreams; the assistant must not compromise the core application.

## 2. Project assumptions and retained boundaries

- All versions target an English-only United States university. Interfaces, announcements, instructions, questions, AI responses, retrieval, and functional acceptance fixtures are English. No translation, localization, cross-language retrieval, or future multilingual extensibility requirement. Do not verify users' nationality or native language; do not impose ASCII-only identity validation.
- University deadline entry/display uses `America/New_York`, regardless of device timezone. Handle seasonal offsets and reject nonexistent spring-forward or ambiguous fall-back wall times. Store instants; preserve existing instants; authoritative backend/database time controls eligibility. See [timezone amendment and evidence](timezone-amendment.md).
- Preserve at most one grouping activity per course, creation-time fixed participants, immutable system-created groups/capacities, and one membership per participant per activity. Enrollment remains separate from activity participation.
- The cutoff is the current deadline. All student grouping mutations, including departure and manager transfer, require the final database-time check to be strictly before it. No grace period, post-cutoff transfer, reopening, reset, teacher membership editing, or change to finalized membership/manager state.
- Preserve automatic expiry, saved-plan random allocation, restart/retry recovery, finalization, teacher-only full roster/CSV, and student own-final-group privacy. System-only post-deadline groups remain managerless.
- Existing teacher activity-instruction edits remain allowed before and after cutoff, including allocation/finalization, and remain text-only changes with no effect on participants, capacities, deadlines, memberships, request outcomes, or allocation plans. Request-history views must still not disclose another student's final assignment.
- Preserve session authentication, browser CSRF protection, separate scheduler authentication, seeded accounts, existing teacher/course assignments, and current authorization on every resource request. No administrator product role or new enrollment-management UI is introduced.
- Planning workload: five courses, forty students per course, ten concurrent assistant users. These are estimates, not course/enrollment hard limits or proven capacity.

## 3. Actors and permission matrix

All permissions below require current session and relevant course access. Group actions additionally require fixed activity participation and pre-cutoff eligibility.

| Capability | Assigned teacher | Enrolled student | Current group manager | Operator outside product UI |
|---|---|---|---|---|
| Create/edit/delete course announcements | Yes, including another assigned teacher's announcement | No | No additional right | No implied product right |
| Read current course announcements | Yes | Yes, including notices predating enrollment | Same student right | No implied product right |
| Leave own group | No teacher power | Yes if currently a member and before cutoff | Yes, with automatic succession if needed | No new manual membership tool |
| Transfer manager role | No | Only if currently manager | To another current member before cutoff | No new control |
| Decide applications/send invitations | No | No unless current manager | Yes, under existing eligibility rules | No |
| Read request history | No | Own applicant/recipient attempts | All attempts for currently managed group | No new reporting feature |
| Ask assistant | Yes, for assigned course | Yes, for enrolled course | Same student right | No new chat viewer |
| Read a student's assistant conversation | No | Only their own in-memory conversation | No access to others | No conversation reporting/database |
| Rebuild derived assistant index | No | No | No | Authorized maintenance operation only |

Course-access revocation removes subsequent source, question, and citation access. A fixed activity participant who is no longer enrolled does not gain assistant access through participation. Already displayed browser text cannot be remotely erased, but no later response or citation may bypass current authorization.

## 4. Group lifecycle requirements

### GL-01 — Voluntary departure

A current member can explicitly leave their own group without approval before cutoff. Recheck current membership, enrollment/participation, resource scope, and authoritative time under the existing activity lock. Successful departure makes the actor ungrouped and frees one place; no other membership moves.

The operation does not reserve the vacated place, guarantee another destination, or guarantee that automatic allocation will avoid the former group. No manager, teacher, or alternate endpoint may use departure to remove someone else.

### GL-02 — Manager departure and empty groups

If the departing member is manager and members remain, select a remaining current member randomly as successor without acceptance. Departure and the one selected successor commit together. A failed transaction performs neither change; duplicate commands and uncertain-response reconciliation cannot rerun the random choice for the same successful departure. Do not introduce preference ranking or AI succession.

If no members remain, keep the existing group identity, ordinal/name, and capacity; clear its manager and system-cancel still-pending applications and invitations with a distinct empty-group reason (display label: `Group empty`). Preserve completed outcomes. The next ungrouped student deliberately joining the empty group becomes manager through the existing first-join route.

### GL-03 — Deliberate manager transfer

The current manager may explicitly select another current member of the same group and transfer authority immediately, before cutoff, without recipient acceptance. The former manager remains an ordinary member. Validate the target at execution time; a stale member selection, different-group target, or nonmanager caller cannot change authority. There is one manager, not co-management or delegated authority. Selecting oneself is not a transfer.

### GL-04 — Pending requests and history after authority changes

A manager change in a still-occupied group preserves pending applications and invitations. The current manager can manage applicable pending requests and read that group's earlier application/invitation history, including invitations originally sent by someone else. Preserve original actor identities and outcomes; do not rewrite historical sender/reviewer facts to the new manager.

Former managers lose group-management history access when authority changes. They retain ordinary access to attempts involving themselves as applicant or invitation recipient. Being an original invitation sender alone does not preserve former-manager access in V2. Apply this rule to every page, direct lookup, cursor, response DTO, and command replay, including after cutoff. Teachers and other members have no request-history access.

### GL-05 — Return, capacity, and request lifecycle

A departing student may immediately join/apply again through ordinary current eligibility checks, including to the former group. No cooldown, automatic restoration, reserved place, or atomic switch workflow. Joining an occupied group requires its normal application/invitation route; joining an empty group can establish manager status through first joining.

Opening a vacancy never resurrects requests previously canceled because the group was full, nor accepted, rejected, expired, or other completed attempts. A new eligible attempt has a new identity and idempotency key. Existing one-pending-per-pair constraints, counterpart independence, join cleanup, and cutoff expiry remain.

### GL-06 — Transaction and cutoff integrity

Use V1 activity-scoped serialization and current-state rechecks for departure, succession, transfer, joining, approval, invitation acceptance, deadline extension, and allocation. Competing commands have a consistent committed order. Former-manager stale approvals/sends fail; departed transfer targets fail; no duplicate membership, overcapacity, or occupied pre-cutoff managerless state results from a successful operation.

Commit membership/authority/request cleanup and successful command receipt together. A lost response requires a read of actual committed state before an explicit retry; never automatically resubmit. Reads are mutation-free. At cutoff, expiry/allocation consumes the resulting committed memberships and reuses the stored allocation plan as before.

### GL-07 — Explicit exclusions

No member removal, application withdrawal, manual invitation cancellation, post-cutoff departure/transfer, teacher assignment/intervention UI, custom group naming, group creation/deletion, capacity editing, or activity reset. Automatic cancellation remains required. This excludes withdrawal/cancellation despite their appearance in the original V2 request (Q10–Q11 supersede that proposal).

## 5. Announcement requirements

### AN-01 — Content and publication

An announcement belongs to one course and has a required nonblank title of at most 150 Unicode code points and required nonblank plain-text body of at most 10,000 code points. Preserve text and render it safely; do not execute HTML/Markdown/scripts. Reject invalid text using existing plain-text conventions. English is the content assumption, not a nationality check or a translation feature.

Creation publishes immediately; there are no drafts, scheduling, attachments, rich formatting, comments, pinning, email/push, notification center, or read receipts. Announcements work without a grouping activity.

### AN-02 — Read and management permissions

Any currently assigned teacher may create, edit, and delete any announcement in that course. Currently enrolled students read all current announcements, including those published before their enrollment. End of enrollment ends read/citation/assistant access. Direct identifiers and cached client views do not grant access to other courses.

### AN-03 — Listing, time, and concurrent changes

Show newest publication first, with publication and last-edited timestamps in the university timezone. Editing does not change publication order; use stable tie-breaking and bounded retrieval for large lists as routine implementation details. Existing no-history/no-restore scope does not prohibit internal revisions needed for correctness.

Edits use a version precondition. A second teacher's stale edit is rejected, preserves the newer content, and offers reload rather than blind overwrite or automatic resubmission. Creation/edit/deletion responses must agree with persisted state after an uncertain response.

### AN-04 — Deletion and AI visibility

Require teacher confirmation before deletion. Successful deletion immediately removes the announcement from normal views and eligibility for new answers; no user restore. Keep only internal version/deletion metadata needed for indexing/citation correctness, not a user-facing content archive. Deleted content must not be served from an index, previous prompt context, or old citation link.

Publish the authoritative source change and its durable indexing work together. An embedding/indexing outage cannot block a valid teacher content save. Until indexing catches up, the assistant may omit new current content but must exclude its obsolete version. See RA-04 and RA-07.

## 6. Course assistant requirements

### RA-01 — Scope, audience, and English-only interaction

An enrolled student or assigned teacher opens the assistant within one selected course. Every question is authorized against current server-side relationships. A conversation never searches another course, even if the same user has access to both. The assistant is available without a grouping activity, clearly stating when none is configured, while using announcements and maintained general rules.

Accept English questions up to the provisional 2,000-character limit; reject unsupported-language questions with an English request to ask in English. Names, quotations, and occasional foreign terms alone do not invalidate an otherwise English question. Answers remain English even when asked to translate or switch languages. English content/retrieval only; no cross-language quality requirement.

### RA-02 — Permitted evidence and non-agentic behavior

Use only current permitted course announcements, existing grouping activity instructions, application-maintained grouping-rule documentation, and current authoritative activity settings needed to interpret those rules. There is no separate assignment entity or assignment-management feature.

Exclude student identities, profiles/About me, memberships, applications, invitations, and rosters from ingestion, retrieval, settings responses, and provider prompts assembled from authoritative records. Answer general eligibility rules; direct personal-status questions to normal group screens. Do not reveal another group's final membership or teacher-only information through generated text or citations.

The assistant performs no business mutation, group join/departure/transfer, teacher content edit, or tool-driven action. Source text and user-provided instructions cannot grant tools, permissions, cross-course retrieval, or authority. No general-knowledge answer fallback, web search, or arbitrary URL ingestion is required.

### RA-03 — Grounding, missing evidence, and conflicts

Retrieve relevant current evidence for every question and follow-up. The prior conversation is context for interpreting the question, never evidence that overrides current sources. No sufficient evidence means an explicit insufficient-information answer, with links to normal permitted course sources where useful.

If teacher text conflicts with enforced rules, cite the conflict, explain the actual enforced rule, and suggest asking the teacher. If announcements conflict and neither explicitly supersedes the other, cite both and request teacher clarification; publication recency alone does not establish precedence. Do not fabricate missing details or silently choose an interpretation.

### RA-04 — Freshness and indexing states

Content changes schedule durable, retryable ingestion after the authoritative save. Use current source/version/deletion checks to exclude stale index entries before they become model context. New unindexed content can be temporarily unavailable. Disclose incomplete sources when indexing is pending/failed/unavailable and link to normal course pages; empty retrieval is not proof that the teacher supplied no information.

Read authoritative activity existence/current settings for every question instead of trusting embedded deadlines. Rule documents are maintained with application behavior releases and are not teacher-editable. Operators can rebuild derived indexes from authoritative content without editing content; no teacher rebuild control.

### RA-05 — Citations and answer delivery

Every substantive factual answer includes clickable references to supporting announcement, activity instructions/settings, or maintained rule section. References identify actual authorized sources/sections; generate links server-side from a validated source manifest rather than trusting model-authored URLs. Cite enough evidence for each factual claim and for both sides of a documented conflict.

Buffer the complete answer. Before delivery, recheck course authorization, activity settings/rule version, and source versions/existence. If relevant content changed or access was revoked during processing, withhold the generated answer and ask for a fresh explicit attempt; no partial streaming. Every citation open performs current authorization and serves only permitted current content.

Earlier answers can remain visible during the temporary conversation. When a source change/deletion is detected on a subsequent check or citation open, mark the earlier citation/answer as changed or unavailable; do not serve an obsolete snapshot. No real-time push subscription is required. After access revocation, give safe unavailable feedback rather than disclosing the underlying source.

### RA-06 — Temporary conversation and provider privacy

Show a brief external-processing notice asking users not to enter sensitive information; promise no automatic detection/redaction. Students can type personal information despite the excluded authoritative records. External processing is permitted; the owner does not require a provider to exclude training use. The chosen provider's actual training/retention/subprocessor and account terms must nevertheless be documented before approval.

Store conversation only in browser memory; leaving/reloading/logging out or switching course ends that conversation. No server conversation database, browser persistent history, application answer cache, or teacher/operator conversation viewer. Request-scoped server memory for bounded processing is permitted. Send only bounded needed follow-up context; filter out source versions no longer eligible and do not use earlier answers to bypass grounding.

Do not log question text, retrieved text, generated answers, or conversation payloads in application logs, access logs, tracing, error reporters, or provider-client debug output. Allow redacted metadata: duration, error class, model/config version, token counts, and internal source identifiers. Secrets remain server-side. Provider retention is separate from application storage and must not be described as zero retention without evidence.

### RA-07 — Free-only operation, throttling, and failure isolation

No paid AI use or fallback. Provider/account setup and resource provisioning need their own authorization; documentation approval does not authorize them. A synthetic-data comparison of free generation and embedding options and owner approval precedes provider selection. No provider or permanent free availability is assumed by this PRD.

Provisional limits: five questions per minute per authenticated user, a maximum 2,000 characters per question, and two in-flight requests per course. Enforce shared server-side course limits across users/tabs, not client-only controls. Provider failures and timeouts count toward short-term limits; provide retry guidance without auto-resubmission. Do not hold a domain transaction or activity lock while calling AI services. Technical token/context/worker budgets are measured during the provider milestone.

At free-quota exhaustion, timeout, provider outage, indexing failure, or disabled configuration, show safe unavailability/incomplete-source feedback and links to course pages. No fabricated answer, paid fallback, or hidden downgrade. Login, announcement writes/reads, grouping, allocation, and exports remain functional without AI. Bound resources so AI cannot starve domain work.

## 7. Acceptance and release gates

All thresholds are release requirements to validate, not existing results. Track PASS / FAIL / NOT RUN with dated configuration, raw counts, workload, and retained failures; distinguish application failures from environment setup errors.

### AC-01 — Functional and concurrency acceptance

Verify GL-01–GL-07 and AN-01–AN-04 with meaningful frontend tests, real PostgreSQL 17 integration/concurrency tests, and real-backend teacher/student browser flows. Include ordinary/manager/last-member departure, successive random succession, deliberate transfer, request history before/after each authority change, vacancy reopening without resurrecting attempts, immediate return, and managerless system-only final groups.

Race departures, transfer versus target departure, former-manager approval versus transfer, last-place joins versus departure, duplicate command keys, lost responses, database rollback, and activity-lock waits crossing cutoff. Check memberships, capacities, manager membership, immutable participants/layout, request outcomes, receipts, and saved allocation plans directly in PostgreSQL. A single remaining member is a valid successor. A failed command leaves no partial state.

Exercise announcement bounds, safe rendering, immediate publication, order/timestamps, two-teacher stale edits, deletion, course-without-activity access, and content saves while AI is down. Verify unchanged V1 expiry/allocation/final results/export behavior.

### AC-02 — Security and privacy acceptance

Any observed cross-course disclosure or unauthorized action in the agreed security tests blocks release of the affected feature; average scores cannot compensate. Cover anonymous/expired/revoked access, unassigned teacher, unenrolled or late-enrolled student, guessed resource/citation IDs, forged course IDs, former-manager history/cursors/replays, direct API mutations, CSRF, malicious source instructions, user prompt injection, malicious generated links/HTML, and revocation during model generation.

Verify no excluded student data reaches the index/provider through authoritative records; no chats survive reload/navigation in persistent browser storage; teachers cannot view student chats; and logs/traces/caches do not retain content. Test the processing notice, English-only feedback, and personal-status routing. A security test pass is bounded evidence, not proof of zero future vulnerabilities.

### AC-03 — Human-reviewed English retrieval and answer benchmark

Use at least sixty frozen reviewed English questions: forty answerable and twenty with insufficient permitted evidence. Include direct facts, paraphrases, exact deadlines, rules/settings, and multi-source questions across isolated synthetic courses. Human reviewers approve expected answers, source/version manifests, required source sets, acceptable abstentions, and final scores. Automated grading assists but cannot alone determine acceptance.

| Measure | Required outcome on the minimum set | Scoring rule |
|---|---|---|
| Fully correct answers | At least 36/40 (90%) | All required facts correct, no material invented statement or omitted condition; abstention on an answerable case fails |
| Retrieval coverage | At least 38/40 (95%) | All necessary supporting sources represented among the top five distinct eligible source results, including multi-source questions |
| Insufficient-evidence abstention | At least 19/20 (95%) | Explicitly states the evidence limitation without guessing a substantive answer |
| Claim grounding | At least 95% | Count individual factual claims in generated substantive answers; a claim is supported only when its attached citation evidence entails it |
| Citation validity/authorization | 100% | Every emitted citation maps to an existing authorized eligible source/section at final delivery; no invented/cross-course targets |

Report numerators/denominators, claim totals, failures, and per-category scores; do not hide poor retrieval behind answer scores or dilute failures with greetings. All necessary sources in the top five means source coverage, not five duplicate chunks from one source. If expanding the set, preserve at least forty answerable/twenty unsupported cases and use the same thresholds with whole-case rounding upward.

Additional deterministic and adversarial cases outside the sixty cover contradictory announcements, teacher/system-rule conflict, instruction injection, edited/deleted content, delayed/missing embeddings, source changes or revocation in flight, malformed provider output, no activity, provider outage, quotas, and earlier-answer freshness. Each must meet its functional/security expected outcome; no aggregate quality score excuses stale disclosure or authorization failure.

Freeze model/embedding IDs, corpus/rules revisions, retrieval/chunking/prompt configuration, question set, and human rubric with every run. Evaluate tuning separately from a frozen acceptance run; material changes require revalidation. A mocked model proves control paths only, not real-provider answer quality.

### AC-04 — Performance, recovery, and quota evidence

- Provisional answer target: at least 90% of requests finish within fifteen seconds with ten simultaneous users and an awake application. Enforce a thirty-second overall answer wait limit; return retry feedback on expiry. Report failure/throttling counts separately; fast errors do not count as successful answers. Cold starts are separately measured and never mixed into a warm-only claim.
- Provisional indexing target: current content searchable within sixty seconds while application and indexing dependencies are available. Measure outage catch-up separately. Stale/deleted versions remain excluded immediately, regardless of indexing latency.
- Load cases include users spread across five courses and concentrated in one course. Two in-flight questions per course means concentrated load can be throttled; report this explicitly instead of claiming all ten same-course users receive concurrent answers.
- Prove restart-safe ingestion retries, duplicate/out-of-order update handling, deletion tombstones, rebuild behavior, model-version index compatibility, resource isolation, zero paid fallback, and no domain-state changes from source/answer reads.
- Measure provider generation and embedding limits together with application/database storage, network, worker connections, and existing scheduler load. No current free-tier suitability is presumed.

### AC-05 — Release separation and operational evidence

Lifecycle improvements and announcements may be accepted/released independently after their gates. Keep the assistant disabled if no owner-approved free provider meets security, quality, performance, and quota constraints; no silent relaxation. Explicit owner approval is needed to revise requirements.

Carry forward private GitHub no-cost CI allowance/actual CI verification, current Docker/full integration/image checks, hosted cold starts, unattended scheduling, missed-run monitoring, recovery, combined quotas, and deferred backup/tuning tracks. Local tests or an authenticated scheduler 204 do not prove hosted reliability. No deployment or account provisioning is authorized here.

## 8. Decision trace and remaining implementation choices

| Interview decisions | Requirement coverage |
|---|---|
| Q1–Q12, Q57–Q58, Q73 | GL-01–GL-07; authority/history and cutoff tests |
| Q13–Q18, Q36–Q37, Q41–Q42, Q60 | AN-01–AN-04; no separate assignments; course without activity |
| Q19–Q25, Q38–Q39, Q43, Q49, Q61 | RA-01–RA-03; approved evidence, conflicts, permissions, current settings |
| Q26–Q29, Q44, Q48, Q62–Q63, Q65, Q71, Q77–Q78 | RA-04–RA-06; ingestion, delivery, citations, temporary context |
| Q30–Q35, Q40, Q51–Q56, Q59, Q64, Q72, Q74–Q76 | Project assumptions; RA-06–RA-07; AC-04–AC-05 |
| Q45–Q47, Q50, Q66–Q70 | Architecture; AC-02–AC-03 |
| Q79 | Owner confirmed shared understanding and authorized final documentation |

No unresolved product choice blocks these documents. Named providers, verified account policies/free quotas, a compatible PostgreSQL vector extension/index representation, released version pins, chunk sizing/ranking parameters, bounded conversation/token budgets, and deployment-specific resource settings remain explicit technical validation work. Resolve routine details with evidence under this scope; return to the owner for provider approval, changed product requirements, recurring cost, security/privacy trade-offs, or major architecture changes.
