# V1 interview demo release

Prepared October 24, 2026 (Asia/Taipei). Local release preparation only; no push, deployment, cloud resource creation, remote CI or paid execution.

## Provenance and scope

- Branch: `codex/v1-interview-demo`.
- Isolated worktree: `C:/Users/wilso/.codex/worktrees/v1-interview-demo/classroom-grouping`.
- Base: `ec091283400e662c6e62725cc8f1276ce46e1aa1`, completed local V1 acceptance.
- Reused the existing attached release worktree at that base. Its frontend files were checked against the timezone changes from `0e0d7263c62ce0f3a933de632ec5e19778f19974`; the mixed commit was not cherry-picked.
- The V2 source checkout remains on `codex/v2-batch-2`. A SHA-256 preservation manifest of its modified/untracked files is retained in ignored local evidence. No stash, reset, clean, branch switch or data reset was used there.

## Included changes

The only runtime changes are the approved New York timezone amendment: `universityTime.ts` resolves wall times using IANA rules, rejects nonexistent/ambiguous wall times and invalid dates, and converts saved instants back into the deadline editor. Activity creation/settings, activity/request/invitation displays, API validation wording and the existing isolated UI prototype use America/New_York. Winter uses UTC-5 and summer UTC-4. No stored deadline instant is rewritten.

Associated unit, real-backend browser and prototype tests use the new labels/offsets. The existing activity-creation browser journey additionally checks winter creation, summer extension, DST rejection without requests, saved-instant preservation and device-zone independence using the original BATCH-101 V1 fixture. No additional fixture is introduced. Current V1 requirement/architecture/flow/roadmap documentation is amended for timezone only; historical test reports retain their original evidence.

Backend source/tests/resources, migrations, Dockerfile, dependency manifests/lockfile, Maven configuration and CI retain identical Git content to the base. The release has V1-V9 only. There are no V2 accounts/courses/fixture additions, lifecycle endpoints, membership departure or manager-transfer controls, announcements, assistant functionality, AI services or migration V10. Original opt-in V1 synthetic fixtures and Unicode/security cases remain intact; English interface scope does not rewrite identity data.

## Verification ledger

Evidence is retained under ignored `.cache/v1-release/`. Historical Slice 12 results do not count as verification of this assembled release.

| Check | Current result |
|---|---|
| Exact frontend timezone provenance and unchanged backend/build/dependency surface | PASS source comparison; packaged jar contains exactly V1-V9; no V2 runtime/fixture markers |
| Node 24 pinned install | PASS `npm ci --no-audit --no-fund` |
| Full frontend suite | PASS 180 tests / 10 files, 16.53 s |
| Production frontend build | PASS TypeScript/Vite; precedes backend packaging |
| Full Java 25 Maven verify / PostgreSQL 17 / ArchUnit | PASS 163 tests, zero failures/errors/skips; BUILD SUCCESS, 6m43s |
| Packaged jar, complete real-backend Chromium suite | PASS all 23 tests, one worker, zero retries, 2.7m; fresh PostgreSQL database, nine applied migrations |
| Prototype Chromium suite | PASS all 7 tests, one worker, zero retries, 14.2s |
| Docker image build/runtime and complete real-backend Chromium suite | PASS corrected current build; all 23 tests, one worker, zero retries, 2.6m; UID 10001, 512 MiB limit, scheduler 401/204, no OOM/restarts |
| Original V2 preservation and final diff review | PASS all 35 modified/untracked files retain identical SHA-256; original branch/head unchanged; diff/whitespace review passed |
| Hosted startup/cold starts, unattended external scheduling, monitoring, quotas, restore and remote private CI | NOT RUN; existing gates remain open |

Docker's engine was initially stopped. Normal Docker Desktop startup recovered it (server 29.7.2); initial pipe errors are environment evidence, not application failures. The tool sandbox initially denied Git access to the managed worktree; approved execution resolved that access restriction. No product/security/dependency workaround was used.

## Next deployment gates

Local success does not establish free-tier suitability or hosted acceptance. Review account plans, actual remaining/shared quotas, no-charge controls, region/PostgreSQL 17 availability and scheduler/monitor options before any resource creation. Synthetic demo-course selection and backup/restore remain deployment preparation decisions. V1's one-activity-per-course/no-reset rule remains in force; reserve unused courses and retain finalized examples. Do not enable `local` profile in cloud. Cloud deployment, push and provisioning require the user's subsequent authorization.
## Retained local verification setup failures

The first runtime attempt stopped before application/browser execution: the initial PostgreSQL bootstrap uses a temporary Unix-socket server, so a socket-only readiness probe raced its shutdown. The harness was corrected to require TCP readiness. The stopped first database/container/volume and logs remain intact.

The second attempt applied migrations but the jar could not bind port 8088. Windows' excluded TCP ranges (8081-8180 after Docker startup) reserved that port even though no process listened. The owned process exited; the readiness wait was interrupted, and its database was explicitly stopped with data retained. No browser suite ran against this attempt. The third attempt uses TCP readiness, detects an exited jar, and uses bind-tested ports 18088/18089. New separate verification databases preserve both earlier attempts; no reset, schema bypass or product workaround was used. These failures do not invalidate the independent full Maven pass and are retained under `.cache/v1-release/initial-runtime-attempt/` and `second-runtime-attempt/`.
The first Docker build failed with `/bin/sh: ./mvnw: not found` because this Windows checkout converted the wrapper's committed LF line endings to CRLF. Normalizing only the local `backend/mvnw` checkout to LF restored the exact Git blob (no backend/Dockerfile change). The initial Docker build log and ledger are retained. Linux cloud checkouts must preserve LF; future Windows Docker builds require the same checkout care. The image-only continuation uses a new database and does not rerun or relabel the already successful full Maven, jar-browser or prototype checks.

Packaged jar SHA-256: `36d457257e075e96e06accdf9082d2bc1c7fd86eda38f826b256224669056b59`. This is the locally built artifact, not a deployment identifier. The mobile real-backend screenshot was visually inspected for New York deadline/offset labeling and containment.
## Final artifacts and cleanup

- Image: `classroom-grouping:v1-interview-20261024`, ID `sha256:66c7e906022a4c275f9a13eb589af13abfb1626583678ac65dfaa2a38f593ff1` (local image ID, not a pushed registry digest).
- Image runtime PostgreSQL: `17.11 (Debian 17.11-1.pgdg13+2)`; jar and image each applied exactly migrations 1 through 9 to their separate fresh databases.
- Image memory samples: 257.2 MiB before / 306.5 MiB after browser verification, configured limit 536870912 bytes, OOMKilled=false and RestartCount=0 at both checks. These are bounded local samples, not peak-load or Render 0.1-CPU evidence.
- Scheduler smoke: anonymous 401, correctly authenticated 204. Production due-work/final-result behavior is additionally exercised by the full integration and real-backend result journeys; 204 alone does not prove all work completed.
- Original source remains `codex/v2-batch-2` at `f183ab0b10bfb9e32e888a2d8641465cca8dee55`; all 35 modified/untracked file hashes match the initial preservation manifest.
- All owned services stopped. Ports 18088, 18089, 55438 and prototype port 5174 are no longer listening. The stopped `v1-demo-release-20261024-app` and verification database containers (`...-db`, `...-r2-db`, `...-r3-db`, `...-r4-db`) and their separate volumes are retained. No existing project service/database/volume was reset or deleted. The first bootstrap container's forced-stop exit 137 had OOMKilled=false; the successful image had no OOM/restarts.
- Verification outputs/logs/screenshots, including failed setup attempts, remain in ignored `.cache/v1-release/`. Browser traces were disabled to avoid retaining session/CSRF tokens. No credentials or environment dumps enter this report or commit.

The runtime file changes are exactly `frontend/src/Activity.tsx`, `ActivitySettings.tsx`, `Requests.tsx`, `Invitations.tsx`, `api.ts`, `prototype/Prototype.tsx` and new `universityTime.ts`. All other included changes are their timezone unit/browser/prototype regressions, current timezone documentation and this release evidence. No new demo provisioning behavior or data migration is included.

**Local Step 1 complete.** No unresolved application failure was observed in the final automated runs. Corrected environment/harness failures are retained above. Cloud provisioning/deployment/push and account/no-cost checks were intentionally NOT RUN. Foundation, hosted cold starts/session continuity/unattended scheduling/missed-run monitoring/combined quotas/backup restore, final cloud resource tuning and private remote CI remain open. The next step is read-only account review and a concrete resource proposal; it does not authorize creating resources.