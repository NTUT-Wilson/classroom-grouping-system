# Slice 2 — Classmate profiles

Implemented and checkpoint-verified on 2026-10-15 as the first part of the authorized Slices 2–7 batch. The PRD and approved technical stack govern behavior. Whole-batch verification and three independent reviews are complete; see [batch evidence](slices-2-7.md). No deployment, remote CI or hosting validation is claimed.

## Implementation

Students read course classmates and edit their own course-specific plain-text introduction. Profiles project only account UUID, name, exact textual Student ID and About me. Teachers have no profile discovery endpoint. Identity supplies its public identity projection; Courses owns introductions and enrollment checks. Profile reads and edits have no activity lifecycle dependency.

Forward migration **V3__course_introductions.sql** adds the composite enrollment foreign key, primary key and PostgreSQL code-point length check. Applied V1/V2 remain unchanged. Java and TypeScript reject malformed Unicode and NUL and count code points, preserving text without normalization. Empty text is permitted. The UI renders escaped text and requires read reconciliation after an uncertain save before another explicit submission.

The separately opt-in batch fixture adds isolated synthetic courses and eight students without changing the existing DEMO roster. It creates no activities and does not overwrite existing credentials or identity/history. See [README](../README.md).

## Verification checkpoint

| Status | Check | Current evidence |
|---|---|---|
| PASS | `frontend/npm test` | 33 tests, four files, including profiles, auth/API and preserved prototype units. |
| PASS | `frontend/npm run build` | TypeScript/Vite succeeded before backend packaging. |
| PASS | Java 25 `backend/.\mvnw.cmd verify` | 36 tests, no failures/errors/skips, PostgreSQL 17 Testcontainers, ArchUnit, upgrade preservation and Foundation recovery; finished 2026-10-15 15:27:56 +08:00. Log `.cache/slices-2-7/slice2-backend-verify.log`. |
| PASS | `frontend/npm run test:e2e` | 13 Chromium tests against the current packaged jar on loopback 8085 and a disposable PostgreSQL 17 database; no retries, 15.6 seconds. Log `.cache/slices-2-7/slice2-e2e-final.log`. |
| PASS | Profile acceptance | Own/peer/course isolation, exact leading-zero IDs, empty/Unicode/plain text, service authorization, CSRF/teacher denial, concurrent upsert, database constraints, read-only discovery, uncertain committed PUT reconciliation without replay. |
| PASS | Browser layout and keyboard inspection | Initial built-in-browser write/look/interact/fix loop and real-backend desktop 1280/narrow 390 screenshot inspection; search, native modal, Escape and trigger restoration. Mock stub evidence remains separately identified. |
| NOT RUN | Whole-batch image/prototype browser regression and independent review | Scheduled after Slices 2–7 integration, before PR readiness. |

The first focused backend run had one failure against an outdated compiled field-feedback message. The integrated full run recompiled the corrected handler and passed the entire Unicode case. The first real-backend browser run passed 11 tests and failed two overstrict keyboard assertions: native Chromium Tab moved to browser chrome (`document.hasFocus=false`, active body), then returned to the dialog. No background control received focus. The test now verifies that boundary and return, modal containment while document-focused, Escape and trigger restoration; all 13 passed. Earlier failures remain in `backend/target/slice2-profiles-tests.log` and `.cache/slices-2-7/slice2-e2e.log`.

All three read-only Researchers and the backend, frontend, and browser-fixture Implementers were spawned with explicit `model="gpt-6.1-sol"`, `reasoning_effort="medium"`, `fork_turns="none"`. The tool returned canonical task identifiers but no independent runtime setting metadata. Exact identifiers, ownership and handoffs are retained in ignored `.cache/slices-2-7/progress.md`.

Private remote CI/no-cost allowances and hosted cold starts, scheduling, monitoring, recovery and combined quotas remain open. The ordinary scheduler retains its authenticated 501 placeholder. Slices 8–12 are excluded from this batch.
