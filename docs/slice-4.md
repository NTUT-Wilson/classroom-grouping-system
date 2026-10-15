# Slice 4 — Instructions, deadline and status

Implemented and checkpoint-verified on 2026-10-15 after Slice 3. Whole-batch independent review and image/runtime checks remain pending.

## Behavior and integrity

Assigned teachers edit only the instruction text or extend only the deadline through separate, strictly typed command DTOs. Unknown and immutable fields are rejected. Instruction validation is shared with creation: nonblank, at most 5,000 valid Unicode code points, preserved plain text. The instruction mutation has no lifecycle predicate. Actual allocation/finalization workflows remain excluded from this batch and are not simulated as evidence.

Each command runs READ COMMITTED, locks the existing activity, and then rereads the persisted actor and current course assignment. Extensions compare the proposed persisted microsecond instant against the currently locked saved deadline, and obtain PostgreSQL `clock_timestamp()` immediately before saving. The saved deadline must still be in the future and the candidate must be strictly later; shortening and reopening are rejected.

Successful edits and durable command-scoped receipts commit together. Matching retries reauthorize and return the latest permitted projection before dynamic cutoff checks; they cannot restore earlier text or deadlines. Changed-input key reuse is rejected. Fixed participants, group IDs/order/capacities and unrelated state are untouched. No migration is needed; applied V1–V4 remain unchanged.

Status/refresh reads derive OPEN or FINALIZING without writing domain rows, showing a partial roster or teacher progress. After cutoff, instruction editing and classmate profiles remain available. Scheduling/allocation and final results remain outside the implementation; the ordinary scheduler retains its authenticated 501 placeholder.

The frontend preserves uncertain drafts, requires a successful GET reconciliation and explicit draft review before another save, and never replays a mutation during refresh/authentication/CSRF recovery. A browser-discovered default-submit issue in the review button was fixed; its regression verifies that review sends no command and only a separate Save sends a new key.

## Current verification

| Status | Check | Evidence |
|---|---|---|
| PASS | `frontend/npm test` | Exact current source: 61 tests across six files. `.cache/slices-2-7/slice4-frontend-test.log`. |
| PASS | `frontend/npm run build` | Current production build before Maven packaging. `slice4-frontend-build.log`. |
| PASS | Built-in browser development loop | Desktop/narrow, escaped long text, cutoff permissions, denial and uncertain-draft reconciliation; screenshots in ignored scratch, labeled synthetic. Temporary stub/Vite stopped. |
| PASS | Java 25 test compilation | Named strict DTOs and 14 test source files. `slice4-testcompile.log`. |
| FAIL | First focused Docker-backed attempt | Sandbox denied `docker_engine` named-pipe access before PostgreSQL assertions; ArchUnit passed. `slice4-focused-sandbox-failure.log`. Elevated CLI confirmed the engine remained available; no repair/reset/fallback. |
| PASS | Java 25 `backend/.\mvnw.cmd verify` | Current complete suite: 64 tests, no failures/errors/skips, PostgreSQL Testcontainers/ArchUnit/recovery; 2:17, finished 2026-10-15 16:07:37 +08:00. `slice4-backend-verify.log`. |
| PASS | `frontend/npm run test:e2e` | All 16 current packaged-jar Chromium workflows, zero retries, 34.8 seconds; disposable `batch_slice4` database. `slice4-e2e.log`. |
| PASS | Genuine cutoff browser workflow | Normal instruction/extension edits and lost committed instruction response; actual short future deadline expires, extension denied, instructions remain editable/student-readable, profiles remain readable/editable. Narrow screenshots inspected. |

The passing new tests cover strict input/authorization/CSRF, post-cutoff text changes, immutable records, current projection replay, observed activity-lock waits crossing cutoff, competing updates, revocation while waiting, rollback and read-only refresh. The elevated full run resolved the sandbox prerequisite failure without a fallback; that failed attempt remains recorded.

Agent identifiers, explicit gpt-6.1-sol/medium/none spawn settings and ownership are recorded in ignored `.cache/slices-2-7/progress.md`; runtime settings are not independently exposed. The usage-limit interruption was reconciled against live Git/process/database state before work resumed. Hosted/remote CI and operational gates remain separate and open.
