# Slice 7 — Invitations and combined behavior

Implemented and checkpoint-verified on 2026-10-15 after Slice 6. Whole-batch verification and three independent reviews are complete; see [batch evidence](slices-2-7.md).

Only a group manager sends invitations, and only the recipient accepts or rejects. Recipients must remain ungrouped, with current course access and fixed activity participation. Invitations reserve no places. Reinvitation after rejection creates a new attempt; earlier outcomes remain private to the sender and recipient and remain readable through authorized cursor pages.

Forward migration V7 adds a separate invitation-attempt table, same-activity participant/group/sender-member references, one pending invitation per group/recipient and indexed retained history. Applications and invitations can coexist for the same pair. Rejecting either changes only the answered attempt.

First joining, application approval and invitation acceptance use one internal membership writer. The accepted request is preserved, the joining student's other pending applications and received invitations in the activity are canceled with “Joined a group”, and remaining requests targeting a newly full group are canceled with “Group full”. These changes, membership and the success receipt commit together. Completed outcomes, other activities and unrelated outgoing invitations are preserved.

Every mutation uses the activity lock, READ COMMITTED, authorization and eligibility rereads after waiting, and PostgreSQL wall time immediately before the first domain write. Matching command replay reauthorizes and returns current permitted history; changed-input key reuse cannot perform another action. At cutoff, all student mutations including rejection stop. Reads derive EXPIRED without writing pending rows, exposing assignments or running deadline processing.

The course retains four navigation tabs. Requests separates Applications and Invitations; its Invitations view also offers manager candidate cards for classmates. This is a routine placement choice within the approved course-centered design. Final recipient eligibility is always checked by the backend. Prototype controls and synthetic clocks remain separate from real workflows.

## Current verification

- PASS: current focused Java 25/PostgreSQL 17.11 suite, 26 tests with zero failures/errors/skips: nine invitation integration, 15 concurrency, fixture preservation and ArchUnit. `.tools/slice7-focused-current.log`, 2:01, finished 17:19:54 +08:00.
- The first focused attempt had one copied test expectation that revoked the sender and incorrectly expected the recipient's independent response to fail. Correcting the test to revoke the acting recipient passed; a separate test now confirms recipient response without sender access. The original failed log is retained.
- PASS: browser discovery, 22 tests across eight files; full execution results are recorded below.
- PASS: exact `frontend/npm test`, 136 tests across nine files, then TypeScript/Vite `npm run build`. Logs `.cache/slices-2-7/slice7-frontend-{test,build}.log`. Built-in browser development loop covered desktop/390px, escaped long text, IDs, Taipei dates, focus, uncertainty/reconciliation and closed history; temporary preview services stopped.
- PASS: Java 25 `backend/.\mvnw.cmd verify`, 121 tests, zero failures/errors/skips; 6:58, finished 17:31:11 +08:00. PostgreSQL Testcontainers, fresh/upgrade migrations, ArchUnit and recovery; `slice7-backend-verify.log`.
- FAIL: first full eight-worker Chromium run, 14 failed/8 passed. All failures preceded feature assertions in login/navigation/loading; a host snapshot showed 52 MB free of 7,896 MB, with no application database lock waits. Traces and logs are retained; this run is not described as passing.
- PASS: unchanged complete `npm run test:e2e -- --workers=1`, all 22 workflows, zero retries/skips, 3.2 minutes, fresh disposable `batch_slice7b` database. `slice7-e2e-serial.log` and `slice7-final-browser-artifacts`. Multi-user journeys and all PostgreSQL concurrency tests remain intact. Lead inspected the actual 390px invitation history screenshot.
- Frontend fixes made before the passing final checks reset state on course changes, ignore late completions, use fresh GET history after mutations, and suppress actions if any authoritative lifecycle read is closed. These have regression tests.

Exact ownership, commands, results and artifacts are recorded in ignored `.cache/slices-2-7/progress.md`. Slices 8–12, allocation/final results/CSV, deployment and remote CI remain excluded; the ordinary authenticated scheduler keeps its intentional 501 response.
