**Current Batch 3 integration and owner acceptance — 2026-11-05 (Asia/Taipei):** The owner explicitly accepted V2 Batch 3 as manually passed without separate hands-on observations at this checkpoint. [PR #7](https://github.com/NTUT-Wilson/classroom-grouping-system/pull/7) merged as `ff1c372`; [post-merge main CI](https://github.com/NTUT-Wilson/classroom-grouping-system/actions/runs/41875602104) completed successfully. The earlier unclaimed/pending statements below record their original checkpoints. V2-5 onward, hosted cold starts, unattended scheduling, monitoring, recovery, combined quotas, backups and deployment remain open. See [Batch 3 evidence](v2-batch-3.md).

**V2 Batch 3 local completion — 2026-11-05:** V2-3a, V2-3b and V2-4 implementation and local automated acceptance are complete on the isolated `codex/v2-batch-3-acceptance` branch. The unchanged corrected tree passed frontend214/build, full PostgreSQL17 Maven213 (zero failures/errors/skips), complete jar29/image29 Chromium and runtime/security/maintenance checks on 2026-11-03. Both independent review cycles are complete; the confirmed P2 was fixed and all three final reviewers reported no confirmed findings. November5 rechecked artifact/preservation and stopped-service/retained-volume evidence, not a new full test run. Original readability/V1 work is preserved. Owner manual acceptance, Batch3 remote CI and hosted gates remain unclaimed; no push, PR, deployment or AI work occurred. The stopped final image retains exit137/OOMfalse; normal process exit is not claimed. See [complete evidence](v2-batch-3.md).

**V2 Batch 2 owner acceptance — 2026-10-29:** The owner reported manual acceptance complete for V2-1a, V2-1b and V2-2 and authorized finalizing the two dependent PRs. See [finalization evidence](v2-batch-2-finalization.md) for current checks and branch boundaries. Implementation is locally complete and manually accepted; merge and deployment remain pending. Announcements remain Not Started and must wait until Batch 2 is merged and separately authorized. Historical local evidence remains dated; Foundation/hosting operational gates remain open.

**V2 Batch2 local acceptance — 2026-10-23:** [Current lifecycle evidence](v2-batch-2.md) records completed V2-1a/1b/2: frontend186/build, full Java25/PostgreSQL17 Maven192 (0 failures/errors/skips), fresh jar25/image25 Chromium (one worker/zero retries), current image UID10001/512MiB/session-CSRF-scheduler checks, three independent reviewers with no actionable findings, and owned-service shutdown with volumes retained. Initial failures are preserved. Foundation/hosting are not fully validated; private remote CI and hosted/operational gates remain open.

# Initial Project Foundation

**Latest V2-0 local acceptance — 2026-10-21:** After the owner restored Docker, the existing V2-0 implementation passed the current pinned application image build, runtime/security checks and all24 image-backed Chromium workflows (one worker,zero retries,3.4m) on fresh PostgreSQL17.11. UID10001,configured512MiB,health200,anonymous/invalid scheduler401,authenticated204,browser-session scheduler denial and browser CSRF passed; no OOM/restarts before/after, bounded294.7–325.1MiB samples. Three concurrent ticks preserved saved plans/memberships/finalization state. Prior dated frontend180/build,Maven163/ArchUnit/PostgreSQL,jar24,prototype7 and focused timezone25 passes are reused, not rerun or relabeled as new. The old stopped V2-0 container was removed without `-v`; its volume remains. Both new owned containers stopped; unrelated services/containers and all volumes preserved. [Exact evidence](v2-0.md). V2-0 and local timezone acceptance are complete; Foundation/hosting remain open for private no-cost CI/actual remote run, hosted cold-start/unattended scheduling/monitoring/recovery/combined quotas, deferred backups/restore/tuning. No development, deployment, commit, push or PR in this verification continuation.

**Earlier V2-0 recovery/recheck (historical) — 2026-10-20–21:** The initial failures below were followed by normal Docker Desktop startup and PASS full Java25/PostgreSQL17.11 Maven163 (0 failures/errors/skips,4:47), final fixture preservation/denial assertions, ArchUnit and packaging. PASS complete current jar Chromium24 (one worker/zero retries,3.4m), including new winter/summer/DST/device-zone journey; Lead built-in browser verified co-teacher/no-activity/draft rejection/outsider denial. Frontend180/build, prototype7 and focused Honolulu25 also passed. Before image build, Docker's engine pipe disappeared again; build failed before execution. A second normal startup on2026-10-21 reported `sailor-ingest.sock` rename/access failure. Current image/runtime/browser acceptance is NOT RUN; container stop failed on absent daemon, so inspect/stop only owned disposable `cg-v2-0-20261020-db` after recovery. Jar process is no longer present; no app image container created. No reset/deletion/repair/native substitute. [Exact commands, failures, cleanup and evidence](v2-0.md). Foundation/hosting remain open: no private remote CI/no-cost allowance or hosted scheduling/cold-start/monitoring/recovery/combined-quota validation; backups/restore/tuning deferred.

**Initial V2-0 attempt (historical) — 2026-10-20:** [V2-0 evidence](v2-0.md) records clean `codex/v2-batch-1` at `e18037e` plus uncommitted fixture/docs changes, Node24.16.0/Temurin25.0.4.1/Maven3.9.9. PASS frontend180/build, prototype Chromium7 (one worker/zero retries), focused Honolulu-timezone25. Full Java25 `mvnw verify` FAIL/environment:29 reported,0 assertion failures,17 Docker/Testcontainers setup errors; production/test compilation and12 non-container tests including ArchUnit passed, packaging did not complete. Final fixture test recompiled but also failed at Docker setup (1 error). Docker29.7.2's Linux-engine pipe was absent inside/outside sandbox. At this initial attempt, real-backend startup/browser/image checks were NOT RUN; the latest recovery note above records the subsequent Maven163 and jar Chromium24 passes and the remaining image gate. Owner-confirmed production204 instructions replace stale current501 wording without changing production code; probe evidence remains separate. Private CI/no-cost allowance and hosted scheduling/cold-start/monitoring/recovery/combined quotas remain open; backups/restore/tuning deferred. No database/service reset, deployment or external execution.

**Technical design checkpoint (2026-10-20):** The owner settled eleven V2 architecture choices and core-first delivery; see the [technical design](v2-architecture.md) and [nineteen-slice roadmap](v2-implementation-roadmap.md). This checkpoint changes documentation only. Named providers, real free-tier suitability, pgvector installation/compatibility and all V2 implementation/acceptance remain unverified. No application, Docker, image, provider, CI or hosted check was run for this design; the dated application evidence below remains historical and its open gates remain open.

**V2 planning (2026-10-19):** [PRD](v2-PRD.md), [architecture](v2-architecture.md), [flows](v2-user-flows.md), and [roadmap](v2-implementation-roadmap.md) are complete planning documents. V2 feature/provider/security/quality/performance acceptance is **NOT RUN**; no new implementation or hosted evidence is claimed by document completion. Prior timezone verification below remains the latest recorded application check, not a fresh run for this planning checkpoint.

**2026-10-19 amendment:** Current English-only/New York requirements and the timezone implementation supersede older timezone descriptions. [Current verification](timezone-amendment.md) records the new Docker/Testcontainers environment failure separately from historical local passes; no new full integration or image pass is claimed.

**Current local implementation:** Slices1–12 are implemented and locally verified, including final complete jar/image/prototype acceptance and three independent source reviews. Read [Batch2 evidence](slices-8-12.md) for the latest checkpoint. The initial2026-10-13 sections below are historical: Courses/Results are implemented, and the production scheduler now returns authenticated204 for bounded real allocation work. Remote CI allowance and hosted/operational gates remain open.

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

## Slices 2–7 local batch — 2026-10-15 (local implementation complete)

Slice 2's current checkpoint passed 33 frontend tests/build, full Java 25 Maven verification (36 tests, no failures/errors/skips, PostgreSQL 17 Testcontainers and ArchUnit), and 13 Chromium workflows against the packaged jar and a separate disposable database. Docker engine 29.7.2 is accessible outside the sandbox; sandbox pipe/network permission failures were resolved by running the authorized checks outside that sandbox, without changing Docker or application security. See [Slice 2 evidence](slice-2.md).

The latest completed batch checkpoint is [Slice 7](slice-7.md): frontend 136 tests/build, full Maven 121 tests with no failures/errors/skips, and all 22 packaged-jar Chromium workflows with one worker and zero retries. The first eight-worker run failed under severe host memory pressure; its traces and classification remain recorded. [Slice 6](slice-6.md) records application integrity evidence. [Slice 5](slice-5.md) records first-joining integrity evidence. [Slice 4](slice-4.md) records instruction/deadline/cutoff checks. [Slice 3](slice-3.md) separately records creation/snapshot/receipt concurrency evidence. The Slice 4 focused sandbox attempt failed on Docker pipe permissions before PostgreSQL assertions; the elevated current full run passed.

Current whole-batch local image build/runtime and all 22 image browser workflows passed with one worker and zero retries against a separate fresh disposable database. Runtime verified UID 10001, configured 512 MiB, health 200, anonymous scheduler 401/authenticated ordinary 501, no OOM flag or restarts before/after the suite. The 317.7 MiB idle sample is not peak or hosted capacity evidence. All seven unchanged prototype tests passed serially after a retained initial parallel-build stall. Three fresh independent reviewers completed with no actionable defects; one optional history-page duplicate-control limitation is recorded in [batch evidence](slices-2-7.md), alongside commands, failures, delegation, delivery order and boundaries.

Private remote CI/no-cost allowance checks, hosted cold starts, unattended scheduling, monitoring, combined quotas and recovery remain open; backups and final resource tuning remain deferred. The ordinary scheduler still intentionally returns authenticated 501. No local check supplies hosted evidence or proves allocation, and Foundation as a whole remains open.

## Batch 2 local start — 2026-10-16

Slices 8–12 are authorized for local implementation, with sequential verified checkpoints; [batch evidence](slices-8-12.md) and [Slice 8](slice-8.md) record progress. Docker engine 29.7.2 is currently reachable outside the sandbox; the initial sandbox pipe denial is a filesystem/environment restriction, not recurrence of the historical Docker socket startup fault. No Docker repair/reset was performed. Current frontend 136 tests/build and 13 focused production-preparation PostgreSQL tests passed. Full backend/browser/review verification remains in progress; no Slice 8 completion or full V1 readiness is claimed.

The production scheduler placeholder is being replaced by durable preparation. An authenticated 204 indicates a bounded processing invocation, not complete allocation or hosted reliability. Foundation probe tests remain distinct from production-allocation evidence. Private remote CI/no-cost allowance and hosted cold starts, unattended external scheduling, missed-run monitoring, combined quotas/recovery, backups and final resource tuning remain open/deferred. Earlier local and 501-placeholder results above remain historical.

Slice8 checkpoint subsequently passed full Maven134 tests with no failures/errors/skips, all22 packaged-jar Chromium workflows, actual production preparation/read-only observations and a fresh independent review; see [Slice8](slice-8.md). The initial full run had7 stale migration-count assertions, corrected without removing data/session-preservation checks. Production preparation is locally verified; assignment/finalization and whole-batch image validation remain for later checkpoints. Hosted/remote gates above remain open.

Slice9 subsequently passed full Maven146 tests (including25 production allocation/recovery cases),4 selected packaged-jar cutoff workflows, actual complete allocation/managerless-group/read-only observations and independent review. V8 saved plans survive V9 upgrade; actual disposable PostgreSQL outage/restart recovers through production DueWork. See [Slice9](slice-9.md). These are local functional checks; no final-result UI, CSV, whole-batch image acceptance, remote CI, hosted reliability or backup restore is claimed by this checkpoint.

Slice10 local checkpoint now passes frontend161/build, full Maven155 PostgreSQL/ArchUnit tests and all23 packaged-jar browser workflows, with authorized complete teacher/student results and independent review. [Slice10](slice-10.md) retains initial failures and exact scope. Current image/CSV/whole-batch acceptance is still pending; hosted and remote gates remain open.

## Batch 2 final local evidence — 2026-10-16

Current final frontend178 tests/build, corrected full Java25 Maven163 tests (0 failures/errors/skips), all23 packaged-jar and all23 current-image Chromium workflows (one worker/zero retries), prototype7 and CI YAML/six Bash syntax checks passed. Real PostgreSQL17.11 covers fresh/upgrade migrations, production preparation/completion/recovery, authorization and CSV; [Slice12](slice-12.md) preserves exact scenarios and initial failures. The one final startup-poll test error was corrected narrowly and reverified through25 focused cases and the complete163-test run; production code was unchanged.

Current local image SHA25637469b16a6f7a9a2fa7a0aa4ddbc3380d714b04b9657d124ce9f2870852b6209 passed UID10001,configured512MiB,health200,anonymous scheduler401/authenticated204,no OOM/restarts before/after the full browser run. Samples281.8/295/316.4/326.9MiB are bounded local observations,not peak or hosted capacity evidence. Only owned temporary app/image/database services were stopped; data and unrelated services remain preserved. Whole-batch review results are recorded in the linked acceptance report.

These results resolve the current local Docker/Testcontainers/image checks, while historical socket/501-placeholder evidence above remains intact. Private remote CI/no-cost allowance, hosted cold starts/unattended external scheduling/missed-run monitoring/combined quotas/recovery remain open; backups/restore arrangements and final resource tuning remain deferred. Foundation/hosting are not fully validated and no hosted readiness is claimed.

## Batch 3 local acceptance follow-up — 2026-11-03

Separately authorized V2-3a/V2-3b/V2-4 announcement/core acceptance is tracked in [Batch 3 evidence](v2-batch-3.md). Batch 2 is merged at `a810706`; the owner-supplied post-merge CI run is baseline evidence, not a Batch 3 remote run. Current implementation includes Courses-owned revisioned announcements, receipts/catalog/outbox, safe deletion, dormant compaction and shell-only maintenance, with AI absent. Initial full frontend/backend/jar/image passes are retained; the independent product review found an inline-deletion navigation issue, which is fixed and undergoing final complete re-verification. Do not mark Foundation or hosted validation complete from these local results. Batch 3 remote CI is NOT RUN; hosted cold-start, unattended scheduling, monitoring, recovery, combined quotas and deferred backups/resource tuning remain open. See the linked record for final counts, review conclusions and cleanup rather than treating earlier pending-integration prose as current status.
