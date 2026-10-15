# Slice 5 — Discovery and first joining

Implemented and checkpoint-verified on 2026-10-15 after Slice 4. Whole-batch verification and three independent reviews are complete; see [batch evidence](slices-2-7.md).

Students with current course access and fixed activity participation can inspect groups, availability, permitted member profiles and manager identity before cutoff. Teachers cannot access this discovery endpoint. Closed projections contain no group roster or own assignment; classmate profiles and introduction editing remain available.

Joining an empty group requires explicit confirmation. The successful student becomes its sole initial member and manager. A competing join returns “This group is no longer empty” and refreshes permitted information without submitting an application. No occupied-group direct join, departure, switching or manager transfer is introduced.

The mutation uses READ COMMITTED, locks the activity, rereads authorization and eligibility, and checks PostgreSQL `clock_timestamp()` immediately before membership insertion. Membership, manager linkage and actor/command-scoped fingerprinted success receipt commit together. Matching replay reauthorizes and returns the currently permitted projection. The internal membership path will also serve accepted requests in subsequent slices.

Forward migration V5 adds membership uniqueness and same-activity participant/group references, plus a deferred manager-to-same-group-member foreign key. Existing migrations and data are preserved. Coherent read-only discovery uses REPEATABLE READ and a final wall-clock guard before returning membership. Courses and Identity supply trusted public profile projections; Grouping does not query their private tables.

The UI never replays a mutation after transport failure, authentication recovery or CSRF renewal. A lost response requires a successful read reconciliation before another explicit action. A successful command whose response crosses cutoff shows the closed screen without assuming failure or revealing a roster.

## Current verification

- PASS: exact frontend `npm test`, 83 tests across seven files, and `npm run build` before backend packaging.
- PASS: built-in browser development loop, including Cancel/Escape focus restoration, sole manager/member confirmation and 390px layout. Synthetic services stopped.
- PASS: eight PostgreSQL joining integration tests after forced recompilation; seven distinct-transaction concurrency tests, fixture preservation and ArchUnit focused checks.
- The first focused run had two stale test-fixture errors. A second run reused stale classes; the final run explicitly recompiled all test sources and passed. Logs remain in ignored `.tools/slice5-join-*.log`.
- PASS: Java 25 `backend/.\mvnw.cmd verify`, 79 tests, zero failures/errors/skips, real PostgreSQL 17 Testcontainers, migrations, ArchUnit and recovery; 4:09, finished 16:26:11 +08:00. Log `.cache/slices-2-7/slice5-backend-verify.log`.
- PASS: `frontend/npm run test:e2e`, all 18 current Chromium workflows, zero retries, 44.6 seconds; packaged jar and disposable `batch_slice5` database. Log `slice5-e2e.log`; retained screenshots under `slice5-final-browser-artifacts`. Lead inspected the actual joined-group screenshot; safe plain text and membership/manager agree. Genuine cutoff, stale-empty conflict and lost committed response reconciliation passed.

Agent identifiers, requested settings, exact commands, service ownership and artifacts are recorded in ignored `.cache/slices-2-7/progress.md`. No deployment, remote CI or hosted validation is implied.
