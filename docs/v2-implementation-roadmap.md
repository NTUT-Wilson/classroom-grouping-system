**V2 Batch 2 owner acceptance — 2026-10-29:** The owner reported manual acceptance complete for V2-1a, V2-1b and V2-2 and authorized finalizing the two dependent PRs. See [finalization evidence](v2-batch-2-finalization.md) for current checks and branch boundaries. Batch 2 is merged at a810706 (PRs #5/#6); the owner supplied passing post-merge CI run 41018460103 as baseline evidence. V2-3a/V2-3b/V2-4 were separately authorized on 2026-10-30; local implementation/automated acceptance and final independent review are complete; owner manual acceptance and Batch3 remote CI remain pending. Deployment remains unauthorized. Historical local evidence remains dated; Foundation/hosting operational gates remain open.

# Classroom Grouping System — V2 implementation roadmap

## Status, authorization, and sequence

Technical delivery plan finalized on **2026-10-20** after eleven explicit architecture decisions, extending the 2026-10-19 Q79 requirements plan. The owner selected **core-first delivery**: accept lifecycle improvements and announcements before the joint free-generation/embedding feasibility milestone. The [V2 PRD](v2-PRD.md) governs behavior, [V2 architecture](v2-architecture.md) defines the design, [V2 flows](v2-user-flows.md) connect journeys, and [project assumptions](project-assumptions.md) apply across versions. Continue from the existing repository and V1 implementation; preserve unrelated work, data, history, and existing version pins.

**No V2 slice is authorized to start by this roadmap.** No code, dependency installation, provider selection, cloud provisioning, paid execution, remote CI run, or deployment is authorized by document completion. Obtain the user's next implementation instruction for the intended slice. Provider selection additionally requires the agreed concrete comparison/owner checkpoint.

Execute the sequence below. Families V2-0 through V2-9 retain their existing names for traceability; the tracker and execution checkpoints split large families into manageable slices. Each slice includes its own necessary migrations, backend authorization, UI integration where behavior is exposed, meaningful tests, and recovery checks; final acceptance does not postpone integrity/security work. Do not start downstream slices without their dependencies and evidence. Domain-only features and the assistant have separate release gates.

## 1. Current baseline and evidence boundary

- V1 roadmap records twelve slices locally completed on earlier checkpoints, including actual production allocation, final results, and CSV. At this design checkpoint the scheduler declared production 204 but `AGENTS.md` still described pending 501. The owner subsequently authorized V2-0 reconciliation on 2026-10-20: current instructions preserve production 204 and bounded real due work; historical 501 observations remain historical. See [current evidence](v2-0.md). Neither response nor historical tests prove current hosted execution.
- The separately authorized project-wide timezone amendment changed current requirements and frontend behavior to America/New_York. Its 2026-10-19 evidence is frontend 180 tests/build and prototype Chromium seven passed. Maven verify reported 29 tests, zero assertion failures, seventeen Docker/Testcontainers setup errors; real-backend browser/image verification is outstanding. See [current timezone evidence](timezone-amendment.md).
- Docker Linux-engine pipe absence was confirmed inside and outside the sandbox at that checkpoint. Do not assume the earlier Docker recovery or a later environment failure is still current: recheck before a new verification claim. The failed check does not erase historical slice evidence or establish a current integration pass.
- Private GitHub no-cost allowance/actual revised CI, hosted cold starts, unattended scheduling, monitoring, recovery, combined quotas, and deferred backup/resource tuning remain open. See [Foundation](foundation.md) and [hosting validation](hosting-validation.md).
- This planning turn adds documentation only. No V2 functional, security, real-provider quality, performance, or hosted test has been executed by writing these documents.

## 2. Progress tracker

Statuses: **Not Started**, **In Progress**, **Completed**. A completed planning document is not a completed implementation slice. Record dated command/configuration/result artifacts and explicit remaining gates before changing status.

| Slice | Status | Dependencies | Actual evidence / remaining work |
|---|---|---|---|
| V2-0. Baseline recovery and acceptance fixtures | Completed | Existing V1 and timezone amendment | 2026-10-20–21 local acceptance: frontend180/build, prototype7, focused timezone25, full Java25/PostgreSQL17 Maven163 (0 failures/errors/skips), jar24; 2026-10-21 current pinned image build/runtime/security and complete image Chromium24 (one worker/zero retries) PASS. UID10001/512MiB/health200/noOOM/restarts0; concurrent ticks preserve allocation state. Old disposable container removed without deleting its volume; new owned containers stopped. Initial failures retained. See [V2-0 evidence](v2-0.md); hosted/private-CI gates remain separate and open |
| V2-1a. Historical actor integrity and current-manager access | Completed | V2-0 | 2026-10-29 owner manual acceptance complete; finalization in two dependent PRs (merged 2026-10-30; baseline main a810706). 2026-10-23 V10 populated/fresh upgrade and current-manager/cursor/replay checks PASS; focused PostgreSQL20, full frontend186/build/Maven192, jar25/image25, independent review/cleanup. See [Batch2 evidence](v2-batch-2.md); external gates remain open |
| V2-1b. Voluntary departure and automatic succession | Completed | V2-1a | 2026-10-29 owner manual acceptance complete; finalization in two dependent PRs (merged 2026-10-30; baseline main a810706). 2026-10-23 GL-01/02/04/05/06 command/UI/receipt/rollback/races PASS in lifecycle26 and full Maven192; G1/return/empty reuse in complete jar25/image25. Reviewed; owned services stopped. See [Batch2 evidence](v2-batch-2.md) |
| V2-2. Deliberate manager transfer | Completed | V2-1b | 2026-10-29 owner manual acceptance complete; finalization in two dependent PRs (merged 2026-10-30; baseline main a810706). 2026-10-23 transfer/inherited authority/target/duplicate/cutoff/revocation/rollback checks PASS in lifecycle26 and full Maven192; G2/lost-response privacy in complete jar25/image25. Independent review/cleanup PASS. See [Batch2 evidence](v2-batch-2.md) |
| V2-3a. Announcement publication, listing and revision-checked edits | Completed | V2-2 merged with Batch 2 | 2026-11-03 corrected-tree PostgreSQL/authorization/replay/rollback/compaction and no-activity publication/read/edit checks PASS; full frontend214/build/Maven213 and jar29/image29. Final reviews clear; preservation/cleanup rechecked November5. See [Batch3 evidence](v2-batch-3.md); owner manual acceptance/remote CI pending |
| V2-3b. Announcement deletion and uncertain-response recovery | Completed | V2-3a | 2026-11-03 confirmed revision-checked deletion, content clearing, tombstones, safe receipts/reconciliation and inline-deletion regression PASS; current jar29/image29. Final reviews clear. See [Batch3 evidence](v2-batch-3.md); owner manual acceptance/remote CI pending |
| V2-4. Core-feature acceptance | Completed | V2-1–V2-3 | Local automated gate complete: 2026-11-03 frontend214/build, full PostgreSQL17 Maven213 (0 failures/errors/skips), complete jar29/image29, security/persisted allocation/CSV/timezone/maintenance and two independent review cycles. November5 preservation/cleanup verified; final image exit137/OOMfalse retained as limitation. [Evidence](v2-batch-3.md). Owner manual acceptance, remote CI and hosted gates remain open |
| V2-5. Joint free-provider and pgvector feasibility | Not Started | V2-4 | Synthetic generation/embedding comparison and owner provider approval outstanding |
| V2-6a. Current source contracts, rules and citation views | Not Started | Approved passing V2-5 | Allowlisted reads/catalogs/instruction outbox/late-enrollee boundaries outstanding |
| V2-6b. Optional Assistant activation and embedding transport | Not Started | V2-6a | Separate Flyway history/core-only startup/approved adapter checks outstanding |
| V2-6c. Durable relay, ingestion and retry recovery | Not Started | V2-6b | Atomic handoff/leases/version-safe publication/retry checks outstanding |
| V2-6d. Rebuild, deletion purge and model-generation transition | Not Started | V2-6c | Maintenance/resume/stale-job/dimension/isolation checks outstanding |
| V2-7a. Exact vector/full-text retrieval and RRF | Not Started | V2-6d | Scoped candidate/evidence assembly/freshness checks outstanding |
| V2-7b. Frozen human-reviewed retrieval benchmark | Not Started | V2-7a | Separate development/acceptance sets and 38/40 evidence outstanding |
| V2-8a. Bounded structured answering and single-turn UI | Not Started | V2-7b | Generation/admission/final-validation/privacy/denial checks outstanding |
| V2-8b. Signed temporary follow-ups and changed references | Not Started | V2-8a | Bounded signed context/fresh retrieval/browser-memory checks outstanding |
| V2-9a. Real-provider quality and adversarial acceptance | Not Started | V2-8b | Human-reviewed quality and extra deterministic/security outcomes outstanding |
| V2-9b. Performance, quota, recovery and core isolation | Not Started | V2-9a | Warm/cold/load/index/rebuild/restart/free-budget evidence outstanding |
| V2-9c. Combined V2 acceptance and evidence handoff | Not Started | V2-9b, V2-4 | Connected regressions/current image/local acceptance; hosted/CI gates separate |

Family dependencies mean all their child slices: V2-1 = 1a/1b, V2-3 = 3a/3b, V2-6 = 6a–6d, V2-7 = 7a/7b, V2-8 = 8a/8b, and V2-9 = 9a–9c. A family is complete only when every child and its family acceptance checks pass. The tracker reflects subsequently authorized V2-0 and Batch 2 work, and separately authorized Batch 3 local implementation/automated acceptance completed with November3 verification and November5 finalization. V2-5 and later remain Not Started and separately gated. The design itself supplied no implementation evidence.

V2-5 failing to find a compliant free pair blocks V2-6 onward; it does not invalidate core-feature acceptance. Keep AI disabled and evaluate alternatives with the owner before finalizing a provider, without paid fallback or lowered thresholds. Core-first order is settled; reordering requires a new owner instruction rather than an implementation agent silently moving feasibility earlier.

## 3. Slice deliverables and acceptance

### V2-0 — Baseline recovery and acceptance fixtures

Reinspect Git/uncommitted work and authoritative docs. Preserve existing `.tools` conveniences, exact dependency pins, project PostgreSQL 17/loopback 5433, and the unrelated service on 5432. Recheck Java 25, Node 24, Docker/Testcontainers access, frontend build, existing real-backend journeys, current New York timezone behavior, startup, and packaged image when feasible.

Prepare synthetic actors/courses for two assigned teachers, unenrolled/unassigned users, a course without an activity, ordinary and sole-manager groups, pending requests, and cutoff races. Use disposable databases and opt-in idempotent provisioning; do not reset project data, overwrite credentials/history, or alter fixed activities to make tests pass.

**Gate:** Document a usable current local integration/E2E baseline or the exact environment blocker. Complete unaffected design/preparation, but do not call full current verification passed when Docker or packaged-backend checks are unavailable. Revalidate the pending timezone full integration/browser/image amendment as part of this baseline; do not mark it complete from frontend tests alone.

### V2-1 — Voluntary departure and automatic succession

Deliver GL-01, GL-02, GL-04–GL-06 with departure commands/UI and authorized current-state reads. Extend current-manager history authorization here, not in a later slice: random succession must never leave the new manager unable to manage or leave the former manager with stale group access.

Use forward Flyway migrations only where needed. Preserve original attempt actors/outcomes and command-receipt semantics. Random manager selection, departure, empty-group cancellation, and success receipt commit together under the existing activity lock/final database-time rule. No new withdrawal/cancellation/removal endpoint or atomic switching feature.

Implement V2-TD1: keep `activity_membership` current-only and retain the deferred current-manager-to-membership FK. Retarget V1 application-manager/invitation-sender historical FKs to fixed activity participants, preserving IDs and history; extend both cancellation-reason checks for `Group empty`. Keep the original actor fields distinct from the current manager and any new actual-decision attribution. Do not fabricate historical reviewers. A successful-key replay after a leave/rejoin cannot leave again or expose former-manager history.

**Acceptance:** Real PostgreSQL tests for ordinary departure, one/multiple successor candidates, sole-manager emptying, empty-group reuse, late/departed/revoked callers, same-key response loss and rollback, competing leaves/joins, vacancy without terminal-request resurrection, and cutoff while waiting on the lock. Verify current-manager private history and former-manager denial on every page/cursor/replay, including old invitations. Real-backend browser journeys G1, succession/history, immediate return, and post-cutoff denial pass.

### V2-2 — Deliberate manager transfer

Deliver GL-03 and the G2 UI/command using current group members as selectable targets. Transfer immediately without acceptance; former manager remains a member. Pending requests and original actors survive.

**Acceptance:** Target-self/other-group/no-longer-member input cannot create a transfer; nonmanager/teacher callers denied; same-key replay safe. Authority update and successful receipt are atomic: inject a persistence failure and prove both roll back with memberships/requests unchanged. Transfer versus target departure, former-manager approval versus transfer, transfer versus stale invite, duplicate transfer, and cutoff races use separate database connections. Verify exactly one member-manager, no membership movement, new history access, former-manager loss, and uncertain-response read reconciliation in the real browser.

### V2-3 — Course announcements

Deliver AN-01–AN-04 within Courses, including course-without-activity access, explicit DTOs, required 150/10,000-character plain-text fields, immediate publication, immutable publication ordering, timestamps, optimistic version checks, and confirmed deletion without restore. Use server-authorized student/teacher views and safe bounded lists.

Establish authoritative source revision/deletion metadata and a minimal durable indexing-intent contract as part of the same source transaction. No embedding/provider dependency is needed to save notices. Backlog processing may remain disabled until V2-6; this slice does not claim current AI indexing.

Use Courses-owned command receipts, not Grouping's activity-bound receipts. Publication/content/revision/catalog update/receipt/outbox must be atomic; deletion clears content while retaining compact metadata. Implement the actor-authorized receipt/current-resource reconciliation flow, including deletion whose first response was lost. Do not add content snapshots to receipts or a source archive. Core migrations must remain runnable without pgvector or an Assistant schema.

Bind receipt fingerprints to command/course/resource and all inputs, including content and expected revision. Verify exact replay versus changed-payload/target key reuse. Establish owner-controlled safe compaction of superseded outbox events while retaining the latest necessary intent/tombstone, so prolonged AI-disabled edits do not accumulate every obsolete revision indefinitely. Test the no-consumer workload, write/compaction races and storage growth; distinct-source metadata/idempotency retention still requires measured headroom, not a promise of unlimited free storage. Maintenance uses authorized host/local shell access in non-web mode, not a product or scheduler endpoint.

**Acceptance:** Positive and denied CRUD/read access including another assigned teacher's announcement, enrollment changes, stale concurrent edits, invalid fields, script-like plain text, publication order after edits, deletion and uncertain responses. Real PostgreSQL verifies save/intent atomicity and failure rollback. Real-backend browser flows A1/A2 work without any AI service and without a grouping activity.

### V2-4 — Core-feature acceptance and independent gate

Run connected G1/G2/A1/A2 journeys plus V1 login/course/profile, request lifecycle, cutoff expiry, preserved-plan allocation/recovery, final privacy, and exact safe CSV regressions against the actual backend. Include New York winter/summer/DST/device-timezone checks, startup and built image checks, keyboard/mobile/long-label behavior, direct access denial, and uncertain mutation responses.

**Gate:** Record PASS / FAIL / NOT RUN and actual database/browser evidence. Lifecycle/announcements can be accepted independently when their gates pass. No assistant is claimed, and feature acceptance itself does not deploy anything or close hosted/remote CI tracks.

### V2-5 — First AI milestone: no-cost provider and storage feasibility

Prepare a concrete comparison using synthetic English announcements, instructions, rules/settings, questions, and the planned workload. Recheck dated official provider terms and actual account capabilities at execution time; this roadmap selects none. Evaluate generation and embedding candidates together, not generation alone.

Record model/version availability, embedding dimensions and index compatibility, training/retention/subprocessors/geographic-processing policy, actual request/token/concurrency quotas, no-charge controls, failure-at-exhaustion behavior, account restrictions, warm/cold successful latency and failures, initial grounding/citation/retrieval results, and total resource usage. Compare external generation and external embedding as a pair behind the planned `GenerationProvider` and `EmbeddingProvider` interfaces, using small Spring HTTP adapters rather than Spring AI. Cover initial indexing, content updates, full rebuild/model replacement, user questions/follow-ups, retries and outage catch-up with measured counts and combined budgets. Validate PostgreSQL 17 + pgvector exact search/English full-text compatibility on local/test/hosting candidates; preserve one database and approved module boundaries.

**Owner checkpoint:** Present the comparison, failed gates, recommended no-cost pair/configuration, and actual privacy/billing consequences before selecting providers. No provisioned paid fallback, guessed policy, or presumed unlimited free tier. Access or quota gaps are NOT RUN/blocked evidence, not failed model-quality scores. If none pass, stop the AI branch, keep it disabled, and request a concrete scope change only if needed. Core work can remain accepted.

**Gate:** Owner-approved free pair and a measured feasible configuration within current constraints. pgvector exact search is the selected direction, not a verified installation; validate its released pin, image/host availability and separate activation. Exact model/dimensions/distance metric, context/token/worker budgets, chunk parameters and initial RRF configuration become reviewable technical configuration. External embeddings remain the initial direction; if no free external option passes, return alternatives to the owner before finalizing a provider. Do not silently switch to local inference or keyword-only answers. Major architecture or privacy changes return to the owner.

### V2-6 — Current source contracts, durable ingestion, and rebuild

Deliver application-maintained versioned grouping-rule sections, assistant-safe public source/settings contracts, readable authorized citation targets, index versioning, and scoped source catalogs/coverage. Extend durable intents to all current instruction/rule updates and backfill existing current announcements/instructions safely. Read current activity existence/settings for each question; do not rely on embedded deadlines.

Implement bounded persisted retries, paragraph/section-aware chunking, approved embeddings, compatible derived storage, deletion tombstones, stale-job rejection, and an operator-only rebuild path. No authoritative personal/student records enter the index. Source saves stay independent of embedding availability.

Apply V2-TD7/9: source-owned transactional outboxes; atomic event receipt/job handoff and conditional owner acknowledgement; no highest-ID-only committed-event watermark. Claim durable jobs with leases/fencing, make no provider call inside a transaction, and reject obsolete source/generation output. Vector-dependent tables use a separate Flyway location/history and explicit activation outside core startup; do not add them as unconditional JPA entities. Missing/failed AI setup must leave core-only startup/health usable. Late-enrolled students use dedicated safe source/citation views authorized by current enrollment, without gaining fixed activity participation. Instruction edits after finalization still create current revision/outbox work without changing allocation state.

**Acceptance:** Real PostgreSQL and controlled provider-adapter tests for atomic source/intent changes, out-of-order jobs, edit/delete during embedding, crash/restart, provider outage, partial rebuild, model-version mismatch, and whole-index rebuild without business-state changes. Verify obsolete versions are immediately ineligible and current content searchable within the provisional sixty-second available-dependencies target. Distinguish real-provider embedding evidence from adapter fixtures. ArchUnit proves allowed read dependencies and excludes internal/domain-command coupling.

### V2-7 — Scoped hybrid retrieval and frozen evaluation corpus

Deliver course-filtered English keyword/semantic candidate retrieval, current-authority/version filtering before provider context, distinct-source ranking, rules/settings evidence, coverage flags, and bounded context. No global cross-course search, user-controlled source grants, personal membership retrieval, or translation pipeline.

Use pgvector exact search plus English full-text search and RRF, without an additional reranker initially. Bound per-source chunk contributions and count distinct required sources. RRF/candidate/abstention tuning uses a separate development set; freeze configuration before acceptance. Check course catalog additions/deletions and current time-derived activity phase as well as retrieved source revisions.

Freeze human-reviewed minimum forty answerable/twenty insufficient-evidence questions with gold answers, source/section versions, required evidence sets, and scoring rubric. Multi-source required-source sets count as a retrieval success only when every required source appears in the top five distinct results. Keep adversarial/change/revocation cases in addition to the sixty.

**Gate:** At least 38/40 retrieval successes on the minimum answerable set; report every miss and denominator. Real PostgreSQL isolation/freshness tests and direct API checks prove unauthorized/stale candidates do not reach evidence assembly. Source absence and incomplete indexing are distinct states. Test no activity, conflict sources, wrong IDs, unassigned teachers, and enrollment loss.

### V2-8 — Grounded course assistant and temporary UI

Deliver R1/R3 complete English answers and validated server-generated citations, explicit abstention/conflict handling, external-processing notice, teacher testing without student-chat access, browser-memory follow-ups, and source-changed/unavailable indicators on checks.

Enforce provisional 2,000-character questions, five questions/minute/user, two in-flight questions/course, thirty-second wait timeout, bounded context/provider budgets, and release of slots on all paths. Buffer output; final-check current access, settings/rules/catalog and source revisions before delivery. No model-directed tools or business mutations, no partial streaming, no persistent transcript/response cache, no logged text, and no automatic resubmission. Keep core features independent of AI availability/configuration.

Apply V2-TD6/8/10: structured plain-text blocks with trusted evidence IDs; bounded signed previous-answer context tied to session/course/text/evidence/catalog/settings/rules versions; and process-local rolling account limits/course slots/global cap on exactly one application instance. Restart resets short-term counters by owner choice, not ingestion state. Expired/stale context is excluded and invalid provenance never trusted. Preserve privacy on key rotation/context loss; signing is not encryption. No previous answer substitutes for fresh retrieval. Response timeout releases the course slot and discards late output, while any still-running transport retains a bounded underlying resource permit until physically complete.

**Acceptance:** Meaningful frontend/API/browser tests for English-only behavior, no activity, insufficient evidence, teacher/system conflict, conflicting announcements, invalid model citations/markup, prompt injection, source edit/delete and revocation while generation is paused, logout/course switch/reload clearing history, forged context/course IDs, failure/throttle feedback, log/content exclusion, and thirty-second output discard. Controlled doubles prove races/contracts; actual approved free-provider runs are required for answer-quality and latency claims.

### V2-9 — Assistant and combined acceptance

Run the frozen human-reviewed English benchmark against the actual approved provider/model and versioned corpus/configuration. Meet all PRD gates: at least 36/40 fully correct, 38/40 all-required-source retrieval, 19/20 correct abstentions, 95% supported factual claims, and 100% valid authorized citation targets. Report exact numerators/denominators and claim-level scoring. Any observed unauthorized action/cross-course disclosure in security scenarios blocks acceptance independently of averages.

Run extra source-change/deletion/revocation/injection/conflict/recovery cases and connected E2E journeys across lifecycle, announcements, citation reads, and assistant failures. Measure warm successful-answer latency (90% within fifteen seconds at planned ten-user load), concentrated-course throttle behavior, thirty-second timeout, cold starts separately, sixty-second available-indexing freshness, outage catch-up, restart/rebuild, free quota exhaustion, and core-operation resource isolation.

**Gate:** Complete local functional/security/quality/performance evidence with the assistant enabled only after all applicable gates pass. A free option missing provisional latency or other accepted constraints requires an explicit owner revision, not quiet relabeling. Report hosted/CI/image/backup gaps separately; no local run or documentation creates a hosted pass or deployment authorization.

## 4. Manageable execution checkpoints

Each row is one separately authorizable, testable slice. Use the tracker dependencies above and the full family acceptance checks in section 3. Keep tests with the behavior they prove; a foundation-only slice does not authorize downstream features. API/schema shapes are specified in [architecture sections 9–13](v2-architecture.md#9-repository-fit-and-physical-data-design).

| Slice | Bounded deliverable | Done evidence before the next slice |
|---|---|---|
| V2-0 | Current baseline recovery, synthetic fixtures and test matrix | Rechecked prerequisites and current frontend/build/backend/browser/image results or exact blockers, preserving existing databases; flag/reconcile the AGENTS 501 versus code 204 instruction conflict before relying on scheduler expectations; unmet integrity prerequisites remain explicit gates |
| V2-1a | Historical actor FK migration, cancellation reason, current-manager history/decision authorization | Upgrade a populated V1 database with pending/completed attempts; preserve actors/outcomes and current manager-member FK. Direct/page/cursor/replay permission tests, including seeded authority changes and former-manager denial; no departure endpoint yet |
| V2-1b | Own departure, random succession/emptying and UI reconciliation | Atomic receipt/membership/manager/cleanup tests; repeated succession, same-key leave/rejoin replay, lost response, rollback, immediate return, last-place join versus departure, competing leaves and lock waits crossing cutoff; real-backend G1 |
| V2-2 | Immediate deliberate manager transfer and UI | Current-member target, same-key replay, injected failure rolls back authority and receipt, target departure, former-manager approval versus transfer and stale invitation races, authority/history loss/gain and final cutoff; real-backend G2 |
| V2-3a | Courses-owned announcements: publish/read/list/edit, receipts and outbox, teacher/student UI | Unicode/plain-text bounds, current course access, two-teacher stale edit, stable publication order/cursors, uncertain create/edit, same-key replay/changed-input conflict and atomic rollback; AI-disabled edit/compaction/storage checks; works with no activity, provider or extension |
| V2-3b | Confirmed revision-checked deletion, metadata tombstones and reconciliation | Body/title removed, current reads unavailable, metadata/event/receipt atomic, stale delete denied, same-key replay/changed-input conflict and lost delete response reconciled, latest deletion intent survives compaction, no restore/archive; real-backend deletion/denial flows |
| V2-4 | Core-feature independent acceptance | Connected lifecycle/announcement/V1 regression, real PostgreSQL and real-backend browsers, timezone/privacy/CSV/image; no AI readiness claim and no hosted gate closed |
| V2-5 | Joint external-generation/embedding and pgvector feasibility report using synthetic data | Dated policies/account evidence, measured full workload budgets, safe no-charge exhaustion behavior, compatibility and latency/initial quality; owner approves a concrete pair or AI stays disabled. Provisioning/calls need their applicable separate authorization |
| V2-6a | Narrow current source/catalog/settings/rules interfaces, instruction outbox and readable citation view | No personal authoritative fields; current enrollment grants safe late-enrollee text access but no grouping; edits/additions/deletions/settings/cutoff/rules change tokens, post-finalization text update safety and ArchUnit boundaries |
| V2-6b | Explicit optional Assistant Flyway activation, compatible vector storage and external embedding adapter | Core-only/no-extension startup, populated-core upgrade, activation failure/mismatch, finite vector/dimension/schema checks, request limits/timeouts/redacted errors, secrets and no transport auto-retry; no ingestion claim yet |
| V2-6c | Outbox relay, chunking, leased durable ingestion and retry worker | Crash before/after job handoff, allocated-ID commit reordering, duplicate events, compaction/relay/write races, expired-worker fencing, update/delete during embedding, failed provider/backoff/restart; authoritative saves stay available and stale text ineligible |
| V2-6d | Operator-only rebuild/purge/generation transition and index coverage | Opt-in non-web maintenance requires host/local shell and backend secret access; browser/teacher/student/scheduler cannot invoke it. Current-source/tombstone reconciliation without business writes, recoverable partial rebuild, incompatible model/dimension isolation, no resurrection, pending/failed disclosure, sixty-second available freshness and separate catch-up |
| V2-7a | Course-scoped exact vector/full-text + RRF retrieval/evidence assembly | Distinct-source results, required current rules/settings, no stale/cross-course context, no-activity/incomplete-source/conflict cases and development-set tuning; no extra reranker |
| V2-7b | Human-approved frozen minimum 40 answerable/20 unsupported corpus and retrieval run | Gold answers/source versions/required sets/rubric approved; at least 38/40 all-required top-five distinct-source retrievals with exact misses recorded; dev and acceptance sets separate |
| V2-8a | Generation adapter, structured answer validation, admission/final checks and single-turn UI | Complete answer/citation rendering, English/notice/personal-status behavior, malformed output/unsafe links/injection, source addition/edit/delete and revocation paused in flight; multi-tab/account/course limits, timeout/disconnect/late-work bounds and core/no-provider tests. Doubles are labeled |
| V2-8b | Signed browser-memory follow-ups and changed/unavailable references | Signature/text/session/course binding, context bounds/expiry/rotation, stale context exclusion and fresh retrieval; dependent stale answers excluded; no persistent chats on navigation/reload/logout, no teacher viewer or content logs |
| V2-9a | Actual-provider answer quality plus extra deterministic/security suite | Human-reviewed 36/40 correctness, 38/40 retrieval, 19/20 abstention, ≥95% claim grounding and 100% authorized citations; all additional race/conflict/injection/source/revocation cases independently pass |
| V2-9b | Performance, restart/rebuild, free quotas and core workload isolation | Ten-user spread/concentrated cases, ≥90% successful warm answers ≤15s, thirty-second response cap, ≤60s available indexing, cold/outage recovery separately, sustained bounded work and core login/grouping/allocation/CSV usable under AI saturation |
| V2-9c | Combined acceptance/evidence handoff with enable/disable and migration compatibility | Connected R1/R2/R3/G1/G2/A1/A2 regressions, current full local checks/image, frozen reproducible configuration and retained failures; no provider enablement without applicable gates, no implied deployment or remote/hosted pass |

Some checkpoints produce infrastructure/read contracts rather than a visible feature. They must still have runnable focused positive/failure/denial evidence and preserve the core build/startup contract. V2-1a's synthetic authority changes are test setup, not a product endpoint. Parent-family checks are cumulative; completion of a migration or adapter alone does not accept departure, indexing, answering or the whole feature.

## 5. Common verification procedure and evidence format

For affected frontend/backend slices, run frontend `npm test` and `npm run build` before backend packaging, then Java 25 Maven Wrapper `verify` (`.\mvnw.cmd verify` on Windows). Preserve pinned Node 24/dependencies and the lockfile. Use real PostgreSQL 17 Testcontainers for integration/concurrency and ArchUnit for boundaries; mocks/UI-only checks are not database evidence. Follow README for opt-in synthetic setup and real-backend Playwright credentials. Run affected startup, browser, image, and Docker checks when feasible.

AI adapter doubles may deliberately pause/return invalid output to reproduce races and failures. Label them clearly; they cannot replace live approved-provider semantic quality, free-policy/actual quota, latency, or cold-start evidence. Provider calls, embeddings, scripts, debug logs, and tracing must obey text/secret redaction and no-paid-use constraints.

For each checkpoint record:

| Field | Required content |
|---|---|
| Scope/configuration | Revision or working-tree state, slice, database/provider/model/prompt/index/corpus versions, environment and workload |
| PASS | Actual command/run, counts, relevant invariants, browser/provider evidence, timing and timestamp |
| FAIL | Application assertion/quality failure versus environment/provider setup failure, retained initial result, correction if any |
| NOT RUN | Missing prerequisites or unexecuted suites; do not call a subset/native fallback/history a full current pass |
| Remaining gates | Local integration/image, remote CI, provider approval/quality/quota, hosted scheduling/cold start/recovery/monitoring/quotas, deferred backups/tuning |
| Cleanup | Stop owned temporary services; preserve persistent project data and unrelated services/work |

Update this progress tracker and current Foundation/hosting evidence with actual results. Do not mark a slice complete because code exists or a review approved the plan. Re-run broader checks only when changes/failures/remaining concerns justify them.

## 6. Explicitly deferred, excluded, and approval checkpoints

Excluded: withdrawal, manual invitation cancellation, member removal, atomic switching, post-cutoff edits to membership/authority, multiple activities/course, activity reset, teacher assignment/intervention, separate assignments, drafts/scheduling/attachments/comments/notifications, multilingual support, general AI/web answers, business-action agents, student-record retrieval, durable chat/history/reporting, and paid fallback.

Settled directions: current-only memberships with participant-backed historical actors; small Spring HTTP provider adapters; initial external embeddings behind `EmbeddingProvider`; pgvector exact search plus English full-text/RRF without a model reranker; structured evidence-linked answer blocks; owner outboxes/durable Assistant jobs; signed bounded temporary context; separate Assistant Flyway activation; one-instance in-memory admission with restart reset; core-first delivery. See V2-TD1–11 in the architecture.

Deferred technical validation: named generation/embedding providers, exact current policies/quotas and compatible released pins, embedding dimensions/distance metric, pgvector image/extension/optional-schema compatibility, prompt/chunk/RRF parameters, signed-context expiry/key operations, token/context/worker/connection budgets, and final hosted resource tuning. These are measured gates, not unresolved permission to replace the settled architecture. Multiple instances require shared admission-control redesign; local embeddings or extra reranking are not implicit fallback choices.

Approval checkpoints: user authorization to implement the next slice; concrete owner-approved provider selection after V2-5; owner approval for changed product requirements, costs, security/privacy trade-offs, or major architecture; separate authorization for external account/cloud provisioning, paid execution, remote CI with verified no-cost allowance, and deployment. Prior session authorization continues to apply where present; do not add redundant confirmations for routine reversible implementation work.

## 7. Documentation verification — 2026-10-20

| Result | Actual scope and evidence |
|---|---|
| PASS | Read-only V1 code/schema/security/boundary review plus two independent documentation consistency audits against the confirmed V2 PRD. Addressed transfer/receipt rollback, explicitly named races, announcement fingerprint scope, operator shell-only privilege, and dormant-outbox compaction/storage checks. The scheduler instruction mismatch is explicitly flagged rather than silently resolved |
| PASS | PowerShell content checks across the technical design, roadmap, Foundation note and hosting note: 50 local file links/anchors resolved, fenced blocks balanced, no trailing whitespace, eleven decision IDs present, nineteen matching tracker/checkpoint IDs all Not Started |
| PASS | Nineteen-slice dependency ordering checked for acyclicity; PRD quality/performance threshold markers retained; Mermaid node/edge references structurally checked |
| PASS | `git diff --check` for tracked working-tree changes; normal Windows LF/CRLF notices only. The separate content checks include the untracked V2 deliverables; nothing was staged or committed |
| NOT RUN | Mermaid parser/render validation. Structural checks do not constitute a rendered diagram pass |
| NOT RUN | Application/frontend/backend/database/browser/Docker/image/provider/load/CI/hosted tests for this documentation-only checkpoint. Existing dated results remain historical; current prerequisites and all implementation gates must be rechecked when their slice is authorized |

Only documentation was edited in this task: the V2 technical design/roadmap and short Foundation/hosting planning notes. Pre-existing unrelated edits/untracked files were preserved. No source, schema migration, dependency/configuration, database, account, provider or deployed resource was changed. Provider selection and all implementation slices remain outstanding; no additional product choice is needed to use this design as the next implementation-planning input.
