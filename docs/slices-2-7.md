# Slices 2–7 batch evidence — 2026-10-15

The authorized batch implements classmate profiles, activity creation, instructions/deadline settings, discovery/first joining, applications, and invitations with combined request cleanup. Each slice passed its checkpoint before dependent implementation began. Individual evidence is retained in [Slice 2](slice-2.md), [Slice 3](slice-3.md), [Slice 4](slice-4.md), [Slice 5](slice-5.md), [Slice 6](slice-6.md), and [Slice 7](slice-7.md).

## Current whole-batch verification

All commands ran locally against the final production source in `106ac4d`, with frontend assets built before backend packaging. Node 24.16, Java 25.0.4, Docker 29.7.2 and PostgreSQL 17.11 were used. Exact dependency pins and lockfiles remain unchanged.

| Check | Result and evidence |
|---|---|
| `frontend: npm test` | **PASS** — 136 tests / nine files; `slice7-frontend-test.log`. |
| `frontend: npm run build` | **PASS** — TypeScript/Vite; `slice7-frontend-build.log`. |
| `backend: .\mvnw.cmd verify` with documented Java 25 | **PASS** — 121 tests, zero failures/errors/skips; 6:58, finished 17:31:11 +08. Real PostgreSQL Testcontainers, concurrent connections, fresh/upgrade migrations, recovery and ArchUnit; `slice7-backend-verify.log`. |
| Packaged jar: `npm run test:e2e -- --workers=1` | **PASS** — all 22 Chromium workflows, zero retries/skips, 3.2 minutes; fresh disposable database `batch_slice7b`; `slice7-e2e-serial.log`. |
| `npm run test:prototype` (CI mode, serial) | **PASS** — all seven unchanged prototype tests, zero retries, 26.4 seconds; `batch-prototype-serial.log`. |
| `docker build --tag cg-slices-2-7:verified .` | **PASS** — local image only; `batch-image-build.log`. |
| Image runtime before and after full browser suite | **PASS** — health 200, UID 10001, configured 512 MiB, anonymous machine scheduler 401 and authenticated ordinary placeholder 501, zero restarts/OOM flag; `batch-image-runtime.log` / `batch-image-runtime-after.log`. |
| Image: `npm run test:e2e -- --workers=1` | **PASS** — all 22 Chromium workflows, zero retries/skips, 3.0 minutes; separate fresh disposable `batch_image` database; `batch-image-e2e.log`. |
| Diff and dependency/prototype preservation | **PASS** — `git diff --check`; dependency manifests, lockfile and prototype source/config/tests unchanged from `04d80cf`. |

Logs and browser artifacts above live under ignored `.cache/slices-2-7/`. Focused backend logs under ignored `.tools/` supplement, rather than replace, full Maven verification. The local image config digest is `sha256:2a098dbf0b5acf03b64cde79c82744b52df97e295f892605a6733e103a64a4b9`. The post-suite idle memory sample of 317.7 MiB is not peak-use or hosted capacity evidence.

The initial eight-worker browser run **failed** 14/22 during login/navigation/loading. Traces showed delayed authentication/session reconciliation before feature assertions; the host had 52 MB free of 7,896 MB and no application database lock waits. The unchanged full suite subsequently passed with one worker, preserving multi-user journeys and independent database concurrency tests. No assertions or time budgets were relaxed for that rerun. An initial prototype run **failed** one test while Docker compilation overlapped; unchanged serial execution passed all seven. Resource contention is a supported inference, not a proven operating-system cause. Original logs, traces and committed synthetic histories are retained.

## Independent review

Cycle 1 is complete. Three fresh read-only reviewers, none of whom authored the implementation, received the governing requirements and attributable diff `04d80cf..106ac4d`:

- `/root/review_integrity`: database integrity, concurrency, deadline and idempotency.
- `/root/review_privacy`: authorization, privacy, module ownership and API exposure.
- `/root/review_product`: product acceptance, UI/failure recovery and test adequacy.

Each spawn requested `model="gpt-6.1-sol"`, `reasoning_effort="medium"`, `fork_turns="none"`. The collaboration tool returned canonical agent identifiers; runtime settings were not independently exposed. All three completed source/test review with no actionable defects. Integrity review traced locking, final clock checks, constraints, cleanup, replay and rollback tests. Privacy review traced session/security boundaries, party filtering, cursor/replay authorization, closed projections and module ownership. Product review inspected source, tests and existing desktop/narrow/dialog/recovery screenshots. Reviewers did not independently rerun tests or interact with the browser; current execution evidence above belongs to Lead's verification. No remediation cycle or further source change was needed.

One optional product observation was confirmed and retained as a minor limitation: `Groups.tsx:47` and `Invitations.tsx:78` suppress duplicate Apply/Invite controls using the loaded history page. An older pending attempt beyond the first 20 newer attempts can leave such a control visible until that page is loaded. A deliberate duplicate submission is safely rejected by backend `APPLICATION_PENDING`/`INVITATION_PENDING` checks and database uniqueness, without another attempt or membership change. This does not violate the authoritative one-pending-attempt rule or privacy; complete pending-state UI metadata and a >20-attempt UI test remain optional follow-up. There are no unresolved material findings, and no findings were dismissed as incorrect.

## Decisions, setup and boundaries

Forward Flyway migrations V3–V7 add introductions, activities/snapshots/groups/receipts, memberships, application attempts and invitation attempts. Applied V1/V2 are preserved. Normal startup applies migrations; Hibernate validates. No database reset is required or authorized. Opt-in synthetic fixtures are additive and preserve existing credentials/history; see [README](../README.md).

Creation serializes on the existing course row shared with provisioning. Later grouping writes serialize per activity at READ COMMITTED, reauthorize after waiting, and use PostgreSQL wall time for the final eligibility check. One joining writer commits membership, accepted request, affected pending-request cleanup and command receipt together. Private cursor history remains retained and reads derive expiration without changing pending rows.

Four course tabs remain; Requests selects Applications or Invitations. Display/input explicitly use Asia/Taipei, text remains escaped plain text, IDs retain leading zeros, and uncertain mutations require read reconciliation before a new explicit attempt. No mutation is automatically replayed after transport, CSRF or authentication failure.

Slices 8–12 remain unimplemented: no durable deadline processing, allocation, final results or CSV export. The ordinary authenticated scheduler intentionally returns 501. No finalization simulation is claimed. Remote private CI/no-cost allowance, hosted cold starts, unattended scheduling/monitoring, quotas/recovery, backups and final resource tuning remain open in [hosting validation](hosting-validation.md). No deployment, cloud provisioning, paid execution, push or PR occurred.

## Proposed six-PR delivery order

The commits are six identifiable change groups on local branch `codex/slice-2-profiles`, starting from `04d80cf`. Recommend six stacked PRs, each targeting its predecessor until merged, then retargeting to the repository default branch. Shared incremental APIs and migrations make independent parallel merging unsuitable.

| Order / proposed title | Commit and contents | Dependency / validation |
|---|---|---|
| 1. Add course-specific classmate profiles | `5a6435d`; public identity projection, Courses introductions, V3, profiles UI/tests/evidence, isolated fixtures | Slice 1; 33 frontend / 36 backend / 13 jar browser checkpoint. |
| 2. Create fixed grouping activities atomically | `a009232`; course-lock API, V4 snapshot/groups/receipts, teacher creation UI and race tests | PR 1; 48 / 51 / 15 checkpoint. |
| 3. Edit instructions and safely extend deadlines | `e9bdf81`; separate text/extension commands, lifecycle-safe reads, final-clock concurrency and UI | PR 2; 61 / 64 / 16 checkpoint. |
| 4. Discover groups and join empty groups atomically | `ac3e77e`; V5, permitted discovery, sole manager/member first join, joining path and receipts | PRs 1–3; 83 / 79 / 18 checkpoint. |
| 5. Add private applications and atomic approval | `c433e20`; V6, private cursor history, manager decisions, shared joining cleanup, Requests UI, separate CI image database | PR 4; 109 / 97 / 20 checkpoint. |
| 6. Add invitations and combined request cleanup | `106ac4d`; V7, recipient decisions, invitation history/UI, cross-type cleanup/races, final batch evidence | PR 5; 136 / 121 / 22 checkpoint plus whole-batch jar/image/prototype checks above. |

The review-sensitive areas are the final deadline checks after lock waits, cleanup ordering across both request types, receipt replay authorization, private cursor history, and UI reconciliation after lost responses. Hosted readiness remains a separate gate.

## Recovery record

The ignored living record `.cache/slices-2-7/progress.md` contains original Git state, requested spawn arguments and returned identifiers for research/implementation/review waves, ownership handoffs, commands/logs, preserved failures, synthetic service names and concrete next steps. It contains no credentials or student data. On resume, read it, inspect Git and actual processes/containers, and reconcile unfinished commands before running mutations. Secrets are confined to separate ignored local fixtures and must never be printed or committed.

Final cleanup stopped only owned containers `cg-slices-2-7-image` and `cg-slices-2-7-db-20261015`, after inspecting their names/images/loopback ports. Both report stopped with no OOM flag. The local image, stopped containers, disposable synthetic databases and owned network `cg-slices-2-7-local` are retained for recovery; no volumes or histories were deleted. Previously owned jar/development servers are stopped. Final listener audit found none on 55435/8085/8086/5173/5174; unrelated PostgreSQL 5432 remains running and was untouched. No changes were made to the project 5433 service. To resume the retained image environment, inspect state, then start the owned database before the owned image and check readiness; do not rerun complete mutation journeys against its already-used activities. Use a new disposable database for another full acceptance run.

The final evidence-only commit belongs with proposed PR 6; it does not change the verified production source. No application tests were rerun solely for those documentation edits; content, links and diff were reviewed instead.
