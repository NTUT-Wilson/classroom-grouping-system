# Slice 8 — Durable deadline preparation

Checkpoint **Completed**, 2026-10-16, before dependent Slice 9. This slice saves allocation plans; it does not apply system memberships, finalize activities, or expose final rosters. See [batch evidence](slices-8-12.md).

The existing machine HTTP, startup catch-up and periodic adapters now call production Grouping preparation. A synchronous single worker handles at most 64 eligible activities per invocation. Each activity uses a separate READ COMMITTED transaction, locks its activity, rereads the deadline using PostgreSQL time, expires only pending applications/invitations, validates participant/membership/manager/capacity integrity, shuffles only ungrouped participants and saves one complete mapping into existing places. Existing memberships/managers and terminal request outcomes remain unchanged. Empty plans are valid.

Forward Flyway V8 adds durable work state, one plan per activity, placement references/count guards, immutability triggers and a deadline index. Placement writes lock the plan header and reject missing/complete headers, including a concurrent uncommitted-header/FK-wait append. Failure recording reacquires the activity in a separate transaction after rollback, reconciles uncertain commits, persists capped 5-second-to-one-hour backoff with saturated attempt counts, or halts integrity faults. Logs record safe classifications/counts without raw exceptions or student data. No teacher retry/progress control is added.

Authenticated ticks return204 for the bounded invocation; HTTP success does not mean all activities completed. Closed API/UI reads continue deriving effective expiry and suppressing memberships with read-only refresh.

## Verification

- **PASS:** frontend `npm test`,136 tests/nine files, then `npm run build`.
- **PASS:** final focused `AllocationPreparationIntegrationTest`,13 tests, zero failures/errors/skips, real PostgreSQL17.11 Testcontainers. Covers production scheduler authentication, actual app startup/shutdown/restart, future/extended deadlines, distinct-transaction concurrency, immutable mapping, empty plan, preserved terminal outcomes/reasons, uncertain plan commit, rollback/backoff, integrity halt and65-activity batching fairness.
- **FAIL retained:** first full Maven run134 tests,7 failures,0 errors/skips,4:11; seven assertions still expected7 applied migrations rather than8. All data/session preservation assertions were retained. Initial focused12 and final13 runs passed.
- **PASS:** corrected full Java25 `backend/.\mvnw.cmd verify`,134 tests,0 failures/errors/skips,5:24, completed16:00:39+08; PostgreSQL integrity/concurrency/recovery, fresh/upgrade migrations, ArchUnit and packaging. Startup assertion now waits up to10 seconds for exact persisted PREPARED state rather than racing the initial timer.
- **PASS:** packaged jar against fresh disposable `batch2_slice8`: complete `npm run test:e2e -- --workers=1`,22 Chromium workflows,zero retries,2.5min. Teacher/student permissions, cutoff, private history, profiles, uncertainty/reconciliation, desktop/narrow layouts and keyboard journeys passed. Lead inspected built-in-browser sign-in and actual390px post-cutoff settings screenshot: no roster/progress or extension control; instructions remain editable.
- **PASS:** actual production tick204 after browser cutoff saved BATCH-506 complete plan/one placement while retaining its one original membership. Closed discovery returned FINALIZING with no groups/own-group ID. Three read refreshes left a combined domain-table digest unchanged. First helper attempt failed before the tick due to PowerShell response-array enumeration; corrected helper passed.
- **PASS:** fresh independent read-only `/root/review_slice8`, requested `gpt-6.1-sol`/low, no actionable findings. Reviewer did not rerun tests or browser workflows. Lead owns execution evidence.
- **PASS:** `git diff --check`. Dependency pins/lockfile and prototype source preserved.
- **NOT RUN at this checkpoint:** new image/prototype whole-batch checks; required at final acceptance. Remote CI/no-cost allowance and hosted cold-start/scheduling/monitoring/quota/recovery remain open; backups/resource tuning deferred.

Logs/reports/screenshots are retained in ignored `.cache/batch-2/`, including both full Maven logs, original reports, final focused report, `slice8-e2e.log`, browser artifacts and production observation logs. Existing project5433/unrelated5432 services and user-owned untracked manual acceptance document are preserved. No push/PR/deployment/provisioning/paid execution occurred.
