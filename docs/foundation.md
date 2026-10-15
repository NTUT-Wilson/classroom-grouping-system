# Initial Project Foundation

**Phase:** Local foundation setup, 2026-10-13. Product behavior remains governed by [PRD.md](PRD.md); technical direction remains governed by [tech-stack.md](tech-stack.md). This work does not implement V1 business workflows or confirm a cloud deployment.

## Established

- `backend/`: Spring Boot 4.1.1 and Java 25, Maven Wrapper 3.9.9, JPA with schema validation, Flyway migrations, PostgreSQL, Actuator.
- `frontend/`: React 19.3.0, Vite 8.3.4, TypeScript 7.0.2, Node 24 tooling, exact dependency versions and npm lockfile.
- Identity, Courses, and Grouping package boundaries; Results is a read-only subpackage. ArchUnit checks existing dependencies and forbids result persistence/command access. Courses and Results currently contain only their boundary declarations.
- Minimal account authentication, explicit opt-in foundation account creation, bcrypt hashes, JDBC session persistence, CSRF bootstrap/login/logout, temporary bounded login throttling, and a sign-in shell.
- Deny-by-default browser access, separate secret-authenticated stateless scheduler adapter, structured logs, correlation IDs, minimal health responses, and restrictive production cookie defaults.
- Local PostgreSQL 17 pinned by image digest, loopback port 5433, persistent project volume; same database major in Testcontainers and CI.
- One non-root multi-stage application image with digest-pinned Java/Node base images and bundled frontend assets.
- GitHub Actions test/build workflow, without deployment.

No grouping entity schema, activity creation, request lifecycle, memberships, allocation plans, CSV business logic, or role/course resource features have been added. The account table is authentication groundwork, not the complete Identity/domain schema. Student identity seed validation belongs with the later student-profile provisioning work; no institutional IDs or names are inserted by this foundation seed.

## Local verification

| Check | Evidence/status |
|---|---|
| Java backend compile/package | Passed with Temurin 25; Boot-managed dependency baseline retained. |
| Backend test suite | 13 tests passed: real PostgreSQL integration, security unit tests, and ArchUnit. |
| Frontend UI/client tests | 4 tests passed using Vitest and React Testing Library. |
| TypeScript/Vite production build | Passed; assets copied into the Spring Boot jar. |
| Browser smoke test | Passed: Chromium against the real same-origin backend; login, reload, logout, and denied subsequent session access. |
| Non-root application image | Built and ran as UID 10001; Chromium sign-in/reload/logout also passed against the container. |
| Container API smoke checks | Passed: health 200, anonymous scheduler 401, authenticated unimplemented scheduler 501, correlation header on rejection. |
| GitHub-hosted CI execution | Workflow configured; not executed remotely in this task. |
| Render/Neon/external scheduling | Not provisioned or deployed; hosting validation remains open. |

Backend integration evidence includes migration/schema startup, database-stored password hashes, persisted authenticated sessions, CSRF-required login/logout and token renewal, session-ID rotation, expiry, application restart, idempotent demo setup, anonymous/forbidden access, and rejection of browser-session scheduler calls.

The scheduling proof uses a **test-only** PostgreSQL table and `DueWork` implementation. A local timer processes a future-due durable probe without browser traffic, and repeated authenticated invocations do not duplicate that probe. This verifies the adapter seam and local feasibility only. The probe is not packaged into the runtime image and does not establish allocation, preserved-plan recovery, or cloud reliability.

The default application scheduler adapter returns **501 Not Implemented** after successful machine authentication. Local scheduling is disabled in ordinary foundation runs. This prevents a false successful completion signal before the allocation feature exists.

The final container's local idle memory sample was approximately 301 MiB. This is a single local observation without a Render-sized enforced limit or representative feature workload; it does not validate free-tier memory or hosting suitability.

## Environment findings

- The machine's active Java was 17. A checksum-verified Temurin 25 JDK was downloaded into ignored `.tools/`; the existing installation was preserved.
- Docker Desktop's engine needed starting outside the sandbox. Docker, Maven/npm cache access, and Vite/browser subprocesses also required execution permission outside the sandbox.
- Existing PostgreSQL occupies port 5432. The project uses port 5433; the existing database/service was not modified.
- Local credentials and caches are development/test infrastructure, not approved hosted production configuration.
- Maven packaging initially encountered a Windows lock from the running verification jar. After stopping that process, the final `mvnw verify` passed all 13 tests and packaging together.

Verification services were stopped after testing. The synthetic `foundation-demo` authentication account was removed so the next explicit demo setup can use the user's own local password. The project database schema/volume and built image were preserved; the existing PostgreSQL service was not changed.

## Incremental validation and remaining gates

Foundation establishes the runnable skeleton, local database connectivity, authentication/session/CSRF basics, CI definition, and a local scheduling feasibility probe. Feature-specific unit, integration, concurrency, security, and E2E tests must grow alongside implementation. Full allocation, original-plan recovery, privacy, export, and complete workflows are required before V1 completion, not before those features exist.

Cloud deployment/provisioning is outside this task's authorization. Render Free cold starts, genuinely unattended external triggering, retry/timeout behavior, missed-execution detection, Neon compute/storage/session usage, and combined free-tier quotas still require an approved hosting validation exercise. Local timer success cannot substitute for that evidence. Neither Render Starter nor paid backup infrastructure has been committed.

Backup frequency, retention, provider/storage, recurring cost, full domain schema/constraints, feature-level recovery tests, and final resource tuning remain deferred as agreed. No numerical availability, workload, processing-latency, or backup guarantee has been introduced.

Run/setup instructions are in [README.md](../README.md). Approved PRD, architecture, product definition, user flows, and glossary were preserved during foundation setup.

## Foundation follow-up — 2026-10-13

The user subsequently authorized completing remaining Foundation work without business features or cloud costs. The preceding verification table records the initial setup; the following is the current follow-up evidence.

### Completed local work

- The browser now fetches a session-backed CSRF token before its startup session check and renews it on reload. Authentication controls wait for startup initialization. A lost mutation response discards the cached token and never triggers an automatic replay.
- Apache Commons CSV **1.14.1** is pinned. A runtime compatibility test writes and reads synthetic quoted Unicode fields successfully; no business CSV export is implemented.
- ArchUnit now also rejects outside access to Courses and Grouping internals, preserving public service boundaries as those modules grow.
- Three recovery tests cover a durable test-only probe caught on startup without browser traffic, authenticated session/connection recovery after PostgreSQL restart, and production Secure/HttpOnly/host-only/SameSite=Lax cookies. Default execution uses Testcontainers; an explicit supplementary native mode uses a disposable loopback PostgreSQL 17 instance.
- A repeatable manual bcrypt benchmark uses the application's unchanged DelegatingPasswordEncoder setting and synthetic input. On Temurin 25.0.4.1, 16 reported processors, five warmups and 20 successful password-verification samples: strength **10**, median **55.69 ms**, p95 **64.38 ms**, maximum **66.55 ms**. These are local observations, not hosted measurements or a security/performance guarantee. No work-factor change was made.
- CI now includes image runtime and Chromium checks under a 512 MiB container memory limit, in addition to image building. Workflow YAML parses and its extracted shell commands pass Bash syntax validation; remote execution remains unverified.
- [hosting-validation.md](hosting-validation.md) records current provider limits, account access, free-only precautions, and the concrete hosted validation checklist.

### Current verification

| Check | Follow-up result |
|---|---|
| Frontend tests | **PASS:** six tests, no failures. |
| TypeScript/Vite build | **PASS:** updated assets generated. |
| Available backend verification and packaging | **PASS:** eight tests (one architecture, three security, one CSV compatibility, three native PostgreSQL recovery); zero failures/errors/skips, packaging successful. This was an explicitly selected subset, not the full suite. |
| Fresh disposable PostgreSQL/application startup | **PASS:** PostgreSQL 17.11, Flyway V1 applied and JPA schema validation/startup succeeded. |
| Real-backend Chromium smoke | **PASS:** startup/reload CSRF bootstrap, login, reload continuity, logout, then session 401. |
| Native database restart recovery | **PASS:** login remained usable after the actual disposable database restart; recovery test also passed. |
| Full Testcontainers suite | **BLOCKED:** Docker Desktop cannot start; the attempted full verification failed at Docker discovery before database integration tests could run. |
| Revised Docker image/runtime/memory check | **NOT RUN:** same Docker blocker. Earlier image passes do not verify these revised files. |
| Revised GitHub-hosted CI | **NOT RUN:** files remain local; repository is private and no-cost runner allowances/spending controls have not been verified. |
| Hosted cold starts, external scheduling, quota usage, missed-execution monitoring | **NOT RUN:** Render/Neon account access remains unavailable. |

Docker's backend reports an inaccessible internal `sailor-ingest.sock` file during startup. A narrowly scoped attempt to remove only its two stale socket files also failed with a Windows file-access error. No Docker reset, volume deletion, or changes to existing PostgreSQL services were performed. A separate native PostgreSQL cluster in ignored workspace cache was used for supplementary verification and stopped afterward.

A native test-runner path/log-handling issue was corrected; the final selected backend verification passed. No unresolved application failure was found in the checks that executed. The original nine Docker-based integration tests still need rerunning on the revised dependency baseline after Docker is repaired.

GitHub was connected during this follow-up and confirmed a private repository. Its current committed baseline has no returned commit-status entries or PR-triggered runs; that lookup does not establish the status of push-triggered runs. Render and Neon integrations were found but are not connected. No cloud resources were provisioned or paid execution triggered.

**Readiness:** local Foundation corrections are complete and the available checks pass. Foundation as a whole remains open until Docker verification and the approved hosted feasibility gate pass. No new product, paid infrastructure, or major architecture decision is required for the completed local work. Hosted validation needs account access and confirmed no-cost execution controls; scheduler/backup operational arrangements remain deferred until that evidence is available.

## Slice 1 local recheck — 2026-10-13

The earlier tables remain historical evidence. During authorized Slice 1 implementation, Docker Desktop became reachable outside the filesystem sandbox: engine 29.7.2; the existing project database and app containers were healthy and were preserved. No Docker repair/reset or persistent database deletion was performed.

- **PASS:** current full `backend/.\mvnw.cmd verify`, 27 tests, zero failures/errors/skips, including PostgreSQL 17 Testcontainers, Foundation integration/recovery, Slice 1 migration/provisioning/access tests, and ArchUnit. Frontend assets were built before packaging.
- The first full run had two recovery-test errors. A separate disposable reproduction showed Docker changing a randomly published host port on container restart (2767 → 2769). `FoundationRecoveryTest` now binds a stable loopback port and checks that mapping after an actual container restart. Its three tests and the subsequent full verification passed; native mode remains supplementary.
- **PASS:** current local non-root image build and runtime, UID 10001, 512 MiB configured memory limit, health 200, anonymous scheduler 401, authenticated placeholder 501, and all eight real-backend Chromium checks against the image. An idle sample of 287.4 MiB with no OOM flag is a local observation, not hosted resource validation.
- **PASS:** frontend 25 tests/build, eight Chromium checks against the packaged jar, and seven preserved mock-prototype checks. Detailed Slice 1 evidence is in [slice-1.md](slice-1.md).
- **NOT RUN:** remote private GitHub CI/no-cost allowance checks; hosted cold starts, unattended scheduling, monitoring, combined quotas and recovery. Backup arrangements and final resource tuning remain deferred.

The previous local Docker/Testcontainers and revised-image gates now have current passing evidence. Foundation as a whole remains open because hosted feasibility and operational gates remain unresolved. No allocation implementation, cloud provisioning, remote CI execution, or deployment occurred.

## Slices 2–7 local batch — 2026-10-15 (in progress)

Slice 2's current checkpoint passed 33 frontend tests/build, full Java 25 Maven verification (36 tests, no failures/errors/skips, PostgreSQL 17 Testcontainers and ArchUnit), and 13 Chromium workflows against the packaged jar and a separate disposable database. Docker engine 29.7.2 is accessible outside the sandbox; sandbox pipe/network permission failures were resolved by running the authorized checks outside that sandbox, without changing Docker or application security. See [Slice 2 evidence](slice-2.md).

The latest completed batch checkpoint is [Slice 7](slice-7.md): frontend 136 tests/build, full Maven 121 tests with no failures/errors/skips, and all 22 packaged-jar Chromium workflows with one worker and zero retries. The first eight-worker run failed under severe host memory pressure; its traces and classification remain recorded. [Slice 6](slice-6.md) records application integrity evidence. [Slice 5](slice-5.md) records first-joining integrity evidence. [Slice 4](slice-4.md) records instruction/deadline/cutoff checks. [Slice 3](slice-3.md) separately records creation/snapshot/receipt concurrency evidence. The Slice 4 focused sandbox attempt failed on Docker pipe permissions before PostgreSQL assertions; the elevated current full run passed. These are local feature checkpoints; the batch remains in progress.

The current batch image/runtime checks and final independent review have not yet run. October 13 image evidence remains historical. Private remote CI/no-cost allowance checks, hosted cold starts, unattended scheduling, monitoring, combined quotas and recovery remain open; backups and final resource tuning remain deferred. The ordinary scheduler still intentionally returns authenticated 501. No local check supplies hosted evidence.
