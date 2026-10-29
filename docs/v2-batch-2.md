# V2 Batch 2 — lifecycle implementation evidence

**Finalization update — 2026-10-29:** The owner reported manual Batch 2 acceptance complete and authorized the planned two PRs. See [current finalization evidence](v2-batch-2-finalization.md). Merge/deployment remain pending; announcements cannot start before Batch 2 is merged and separately authorized. The implementation/checkpoint record below describes 2026-10-23, including its then-uncommitted state.

Authorized scope: V2-1a historical actors/current-manager authority, V2-1b voluntary departure/succession, V2-2 deliberate manager transfer. Started 2026-10-23 on clean `codex/v2-batch-2` at `f183ab0b10bfb9e32e888a2d8641465cca8dee55`, one worktree. No announcements, Assistant, providers, dependencies, push, PR, deployment, remote CI or cloud provisioning.

All three authorized slices are **locally complete** on 2026-10-23: whole-batch verification, independent review and owned-service cleanup passed. Historical V2-0 passes are not current Batch2 verification. External gates remain open.

## Implementation

V10 replaces only historical application manager/invitation sender membership FKs with fixed-participant FKs, extends cancellation reasons with `Group empty`, and adds group history indexes. Current memberships, participant/group constraints, deferred current-manager membership FK, attempts, actors and receipts remain intact. Application `manager` means manager at submission; invitation `sender` remains original sender. No decision attribution is invented.

History/cursors/request receipt responses check current manager or own applicant/recipient access. New managers may decide inherited pending applications; former managers retain no sender-only management history. New narrowly matched student commands use existing activity serialization, session actor/CSRF, strict redacted DTOs, final PostgreSQL wall-clock checks and atomic receipts. Leave removes only self membership, selects one uniformly random remaining member with Java randomness if needed, and empties fixed groups with pending-only cancellation. Transfer changes authority only. Replays return currently permitted discovery without executing again.

Groups offers explicit departure/transfer dialogs, conservative read reconciliation after uncertain responses, and normal return routes. Request controls derive current authority; historical labels are explicit. History navigation replaces private pages to discard stale management data. No automatic mutation retry.

## Automated verification — 2026-10-23

Windows, Node24.16.0/npm11.3.0, Temurin25.0.4.1, pinned PostgreSQL17.11. Scratch paths below are ignored local artifacts, not portable test prerequisites. Docker sandbox pipe access is denied; elevated Docker Linux engine check passed.

| Result | Check | Evidence |
|---|---|---|
| PASS | `npm test -- --maxWorkers=1` | Full frontend186/186 across10 files; `.cache/v2-batch-2/frontend-current.log` |
| PASS | `npm run build` | TypeScript/Vite current production assets; `.cache/v2-batch-2/frontend-build-current.log` |
| PASS | Java25 `mvnw.cmd -Dtest=ApplicationIntegrationTest,InvitationIntegrationTest test` | Real PostgreSQL17 Testcontainers20, zero failures/errors/skips; `.tools/v2-1a-focused-current.log` |
| PASS | Focused lifecycle integration/concurrency | 26 unique new tests: concurrency11 and final integration15, zero failures/errors/skips. `.tools/v2-lifecycle-current.log`, `.tools/v2-lifecycle-denials-current.log`. Earlier combined25 had2 failures/1 error, retained below |
| PASS | Java25 `.\mvnw.cmd verify` | Full192 tests, zero failures/errors/skips, ArchUnit, real PostgreSQL17/restart recovery and current jar packaging;6:11. `.cache/v2-batch-2/maven-verify.log` |
| PASS | Fresh packaged jar `npm run test:e2e -- --workers=1` | Complete25/25 Chromium workflows, zero retries,3.8m; fresh `v2_batch2_jar` on8094, PostgreSQL17.11 on55442; `.cache/v2-batch-2/jar-browser.log`, screenshots in `jar-browser-artifacts/` |
| PASS | Current image build | `docker build -t classroom-grouping:v2-batch2 .` rebuilt frontend/backend from pinned inputs; image manifest `sha256:d445f38062060bbf1d22f78167290613947a5cbc61cf5333584c3d87c9361840`. Packaging uses `-DskipTests`, not a repeat Maven verification. Build log `.cache/v2-batch-2/image-build.log` |
| Observed | Built-in browser development | Actual backend on isolated dev DB, Vite5192; departure/transfer dialogs readable, Cancel initially focused and restores trigger; current jar transfer confirmed ordinary membership/removed transfer controls, then ordinary departure confirmed ungrouped/normal return options; no console errors. Screenshots `.cache/v2-batch-2/builtin-transfer-confirmed.png`, `builtin-departure-confirmed.png`. Built-in datetime fill failed; authenticated HTTP fixture setup used. No persistent activity reset |

Retained failures: first frontend180 run had three timeout/timing failures under parallel load and one obsolete loading-label assertion; single-worker186 rerun passed after label update. Build initially rejected unsupported Testing Library `exact` test options; corrected and current build passed. First backend authority test compile overlapped a not-yet-final helper; next run exposed inferred PostgreSQL constraint-name mismatch. V10 now resolves old historical FK names through metadata; current focused20 passes. Initial lifecycle suite also had a second-course fixture setup error, corrected in its rerun. Invalid-target checks exposed missing `targetMemberId` safe-validation allow-list, returning500; corrected and current focused/full suites passed. All failures used disposable tests, not project data.

| Result | Check | Evidence |
|---|---|---|
| PASS | Image runtime/security | Isolated `cg-v2-batch2-20261023-app` on8093 with separate fresh `v2_batch2_image`; UID10001, enforced536870912bytes, health/static200, noOOM/restarts0, sampled284.4–324MiB. Session/CSRF/scheduler separation11checks PASS. `.cache/v2-batch-2/image-runtime.log`, `image-security.log` |
| PASS | Complete image `npm run test:e2e -- --workers=1` |25/25 Chromium, zero retries,5.2m; `.cache/v2-batch-2/image-browser.log`, `image-browser-artifacts/` |
| PASS | Three concurrent production ticks after browser completion | All204; activity/group/participant/membership/saved-plan/work snapshot unchanged, SHA256 `E46A8958708DFD111B43B06B6C1629001202BCE2605856E0F8C4209521AD1EC2`. This proves post-finalization snapshot preservation, not all scheduling races or hosted execution |

## Remaining gates

Local Batch2 acceptance is complete. Private GitHub no-cost allowance/remote CI, hosted cold starts/unattended scheduling/monitoring/recovery/combined quotas remain open; backup arrangements and final resource tuning remain deferred. Local results close none of those gates. Production scheduler remains authenticated204 for bounded due work.

## Independent review

One review cycle after whole-feature verification passed. Fresh read-only `review_product`, `review_security`, and `review_integrity` reviewed the actual diff and untracked source/tests. Lenses: GL/G1/G2/roadmap/UI reconciliation; current-manager authority/cursor/replay/session/CSRF privacy; migrations/data/transactions/cutoff/concurrency and evidence. Each reported **no actionable findings**. No finding was dismissed, no review remediation was needed, and no unresolved application concern was reported. Reviewers did not rerun tests; supplied execution evidence remains separate from code review.

Researcher-to-Implementer workstreams `authority_research` and `lifecycle_research`, E2E Implementer `browser_tests`, and all three Reviewers used exactly `gpt-6.1-sol`, `medium`, `fork_turns="none"`. The Lead ran as launched, integrated the UI, drove actual-backend browser development and whole-batch checks. No roster substitution.

## Architecture, setup and retained limits

No major architecture deviation or dependency change. V2-TD1 uses current-only membership plus fixed-participant historical actors; no membership episodes or invented reviewer audit. Existing aggregate locking/receipts, module boundaries, one-origin deployment model and production bounded scheduler204 remain. Private history navigation replaces the displayed page on Load older instead of retaining previously fetched management pages; Refresh returns to the first page.

Flyway applies new `V10__historical_request_actors.sql` normally on fresh/upgrade startup; no applied migration was rewritten. No extra product configuration or manual data migration. Acceptance opted into existing demo/roster/batch fixtures with generated server/Node-only credentials, disabled scheduler timers, PostgreSQL17 and separate fresh jar/image databases. Runtime helper scripts and secrets stay ignored under `.cache/v2-batch-2/`; local JDK/cache paths are conveniences, not dependencies. Built image packaging used skipTests; the separate full Maven192 run supplies integration evidence.

## Planned pull requests and file grouping — 2026-10-23

1. **Preserve historical request actors and enforce current-manager authority** — V2-1a; no dependency beyond V2-0. Contents: V10; `DatabaseGroupingApplications` and `DatabaseGroupingInvitations`; application/invitation integrity, upgrade and authority tests plus migration-count updates in existing course/platform/grouping tests; Requests current-authority controls, historical labels, private pagination and their tests. Validation: focused PostgreSQL20 plus whole-batch frontend186/build, Maven192 and jar/image25 evidence above. Keep departure/return copy changes in the dependent PR.
2. **Allow voluntary departure, atomic succession and manager transfer** — V2-1b and V2-2 together; depends on PR1. Contents: `GroupingMemberships`, its controller/implementation, validation and narrow security routes; Groups lifecycle dialogs/actions, API helpers, wrapping, updated departure/return copy; three new `GroupLifecycle*` test files (26 tests), `v2-lifecycle.spec.ts`, README and evidence/roadmap updates. Validation: focused lifecycle26, full frontend186/build, Maven192, jar25/image25 and runtime/security. Leave and transfer share the same aggregate writer, receipt plumbing, UI reconciliation and combined races; separating them into two further PRs adds a fragile intermediate split without improving review.

Prospective PRs are outcome groups, not agent boundaries; staged hunks will separate shared frontend files. No branch was pushed and no PR was opened. Current work remains uncommitted on `codex/v2-batch-2` at baseline `f183ab0b10bfb9e32e888a2d8641465cca8dee55`, in the original single worktree.

Inspect most carefully: V10 preservation/deferred FK behavior; every history/cursor/receipt authority path; receipt replay after rejoin/role loss; pending-only empty-group cleanup and uniformly eligible succession; activity-lock cutoff/revocation ordering; frontend state reconciliation after uncertain outcomes.

## Cleanup and checkpoint state — 2026-10-23

PASS: identity-verified `cg-v2-batch2-20261023-app` and `cg-v2-batch2-20261023-db` stopped normally; app exit143 after requested termination, database exit0, both OOMfalse/restarts0. Their stopped containers, built image and PostgreSQL volume `c2a8875dc63a9922ac156ed50260f6681a39c2871cccaf6c4b0a64822bb9333e` remain; volume existence reverified. `.cache/v2-batch-2/cleanup.log` records before/after identities, memory and shutdown. All owned jar processes and Vite were stopped; built-in test tab closed. No persistent volume/database, project5433 or unrelated5432 service was deleted/reset/stopped. Final running Docker inventory was empty; no unrelated container was stopped.

Lead also opened the accepted image in the built-in browser and observed inherited request history, immutable manager-at-submission labels, preserved terminal outcomes and no console errors/warnings. Screenshot `.cache/v2-batch-2/builtin-image-history.png`. E2E screenshots include current manager/empty reuse at390px, inherited history, uncertain responses, final results and timezone behavior. Final `git diff --check` and content review PASS; only line-ending normalization warnings. All source changes remain uncommitted for review; no push, PR, deployment or remote CI run.
