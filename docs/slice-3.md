# Slice 3 — Activity creation

Implemented and checkpoint-verified on 2026-10-15 after Slice 2. Whole-batch independent review and image/runtime checks remain pending.

## Implementation and decisions

Assigned teachers create one immediately open activity per course after reviewing required plain-text instructions, an explicitly Asia/Taipei deadline, and an ordered manual capacity list. The public Courses boundary locks the existing course row and then rechecks the persisted teacher assignment and complete enrollment. Grouping owns the fixed participant snapshot, activity and groups, and uses a READ COMMITTED transaction. Existing roster provisioning uses the same course-row lock.

Forward **V4__grouping_activity_creation.sql** adds unique course/activity ownership, participant and group references, ordered group uniqueness and valid capacities. Every enrolled student is included at successful creation. Instructions permit at most 5,000 valid Unicode code points, remain unnormalized, and must contain nonblank text. Java and TypeScript use the same explicit whitespace set, including NBSP/NEL, for blank checks. Strict JSON types prevent fractional/string capacities from being coerced; totals use a long over bounded integer entries and must equal the full enrolled count. No extra product capacity limit is introduced.

Durable receipts are scoped by actor, command and UUID key. A SHA-256 fingerprint covers the course, exact instruction text, deadline instant and ordered capacities using length-delimited encoding. Claiming a receipt and saving the activity, complete snapshot and every group commit together. Its nonnull deferred activity foreign key prevents a committed receipt without an activity. Matching replay reauthorizes and projects currently permitted metadata; changed-input reuse cannot execute. The UI reconciles uncertain creation through a read before permitting another explicit attempt.

Deadline entry/display is labeled Asia/Taipei independently of device timezone; PostgreSQL `clock_timestamp()` supplies the final future check after lock waits. Storage validation follows PostgreSQL's [timestamp range and microsecond resolution](https://www.postgresql.org/docs/17/datatype-datetime.html); the receipt uses [deferred foreign-key checking](https://www.postgresql.org/docs/17/sql-set-constraints.html). These are storage choices, not new business rules.

Activity reads contain instructions, deadline, effective open/finalizing status and immutable capacity layout. They expose no membership, requests or live progress. Existing activity access requires current course authorization and fixed student participation. Status reads mutate no domain state. The ordinary scheduler remains authenticated 501; allocation and final results remain excluded.

## Current evidence

| Status | Check | Evidence |
|---|---|---|
| PASS | Java 25 focused compilation | 45 main and 12 test sources; `.cache/slices-2-7/slice3-compile.log`. |
| PASS | PostgreSQL 17 focused tests | 15 integration/concurrency tests, no failures/errors/skips; `.cache/slices-2-7/slice3-focused.log`. |
| PASS | Concurrency and rollback | Distinct connections/backend PIDs and observed `pg_blocking_pids` prove actual lock waits. Cases cover competing creation/receipts, course/enrollment coordination, teacher revocation, crossing cutoff while waiting, and injected second-group failure rolling back activity/snapshot/groups/receipt. |
| PASS | `frontend/npm test` and `npm run build` | Exact final source: 48 tests across five files; production build before backend packaging. |
| PASS | Java 25 `backend/.\mvnw.cmd verify` | 51 tests, no failures/errors/skips, full PostgreSQL/ArchUnit/recovery and packaging; finished 2026-10-15 15:46:06 +08:00, 1:30. `.cache/slices-2-7/slice3-backend-verify.log`. |
| PASS | `frontend/npm run test:e2e` | All 15 current real-backend Chromium tests, no retries, 35.0 seconds. Current jar on loopback 8085; new disposable `batch_slice3b` database. `.cache/slices-2-7/slice3-e2e-final.log`. |
| PASS | Browser acceptance and visual review | Manual setup/review, escaped instructions, ordered capacities, immediate student visibility, role/course/CSRF denial, timezone contexts outside Taipei, committed lost-response reconciliation without POST replay; desktop/student and narrow teacher screenshots inspected. |

The first browser run passed nine tests and failed six. Two assertions were outdated (a student POST is denied with 403 by the role filter; a nonexact Refresh locator also matched Refresh activity). Three long journeys exhausted the cumulative 30-second budget during late screenshots/actions; traces showed responsive completed API calls (under 1.5 seconds), no API 5xx, and no blocked database transaction or app error. Those three journeys now explicitly use `test.slow()`; action/assertion limits and zero retries remain unchanged. The corrected full suite passed. The failed run and traces remain in `.cache/slices-2-7/slice3-e2e.log` and `slice3-first-browser-artifacts/`. Its committed activity is preserved in its original disposable database; the successful rerun used a new database, without resetting activity data.

Source/test ownership and explicit gpt-6.1-sol/medium/none spawn arguments are recorded in ignored `.cache/slices-2-7/progress.md`. The tools expose canonical identifiers, not independent runtime-setting metadata. No remote CI, push, PR, deployment or paid execution occurred. Hosted and operational gates remain separately open.
