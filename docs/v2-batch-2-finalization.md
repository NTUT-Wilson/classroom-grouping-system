# V2 Batch 2 — owner acceptance and PR finalization

## Owner acceptance — 2026-10-29 (Asia/Taipei)

The owner reported **manual acceptance of V2 Batch 2 complete** for V2-1a, V2-1b and V2-2, then authorized recording evidence, updating progress, preserving the working tree and creating the two planned commits/branches/PRs. No additional manual observations are inferred from that confirmation. The 2026-10-23 automated/browser/image/review evidence remains [dated historical evidence](v2-batch-2.md).

All three slices are implemented locally and manually accepted. PR integration is pending. **Do not merge or deploy under this finalization authorization. Announcements remain Not Started and must not start until Batch 2 is merged and separately authorized.** No announcement, Assistant, provider, dependency, schema reset or cloud work was added.

## Two dependent PRs

| PR | Scope | Head | Base |
|---|---|---|---|
| [PR 1: V2-1a](https://github.com/NTUT-Wilson/classroom-grouping-system/pull/5) | Historical request actors, forward V10 migration, current-manager authorization, private history pages/labels and integrity/authority tests | `codex/v2-1a-request-authority` | `main` |
| PR 2: V2-1b and V2-2 | Own departure, atomic random succession/empty-group cleanup, deliberate manager transfer, UI/API/security, lifecycle tests, acceptance evidence and roadmap | `codex/v2-lifecycle` | `codex/v2-1a-request-authority` |

PR1 commit: `b7748bfa908743be68905a4b0b43db02f907771e`, based on `main` at `f183ab0b10bfb9e32e888a2d8641465cca8dee55`. PR2 is stacked so its diff excludes PR1. After PR1 is merged, retarget PR2 to `main`, review its resulting diff and rerun required checks before merging. Neither PR is merged by this task; auto-merge is not enabled.

## Fresh verification — 2026-10-29

| Result | Check | Evidence |
|---|---|---|
| PASS | PR1 frontend and affected browser workflows | 182 frontend tests/build; 4/4 selected request/invitation Chromium workflows, one worker, zero retries. [PR1 evidence](v2-1a-finalization.md) |
| PASS tests / FAIL packaging (environment), recovered | PR1 complete backend suite and packaging | 166 tests, zero failures/errors/skips. Standard `verify` failed only when Windows could not rename the jar held by the manual-acceptance app. Unique-name packaging then passed with tests skipped; this is not relabeled a successful standard `verify`. [Exact commands](v2-1a-finalization.md) |
| PASS | PR2 `npm test -- --maxWorkers=1` | All 186 tests across 10 files; `.cache/v2-batch-2-finalize/pr2-frontend.log` |
| PASS | PR2 `npm run build` | Current TypeScript/Vite assets built before backend packaging; `.cache/v2-batch-2-finalize/pr2-build.log` |
| FAIL (verification configuration) | PR2 complete Java 25/PostgreSQL 17 suite | All 192 tests ran: 191 passed; only ArchUnit failed because the temporary `target/finalize-pr2/test-classes` location bypassed its standard test-class exclusion and imported test fixtures. Zero errors/skips; no database/service test failure. This is not a full verification pass. `.cache/v2-batch-2-finalize/pr2-maven.log`, retained copy `pr2-maven-output-path-failure.log` |
| PASS | Corrected-path architecture check and packaging | `mvnw.cmd -f .finalize-verification.pom.xml -Dtest=ArchitectureTest verify`: 1/1 ArchUnit test and fresh executable-jar packaging passed. Temporary POM differs only by `build.directory=../.cache/v2-batch-2-finalize/pr2/target` and `build.finalName=classroom-grouping-finalize-pr2`; the `target/test-classes` suffix restores the normal exclusion. This is a selected recovery, not a full suite rerun. `.cache/v2-batch-2-finalize/pr2-architecture-package.log` |
| PASS | Complete fresh PR2 packaged-jar Chromium suite | All 25/25 workflows passed in 4.3 minutes, including lifecycle/lost-response/history/privacy, retained V1 results and timezone checks. Fresh `v2_finalize_pr2` database, one worker, zero retries; `.cache/v2-batch-2-finalize/pr2-browser.log` |
| PASS | Final content/diff/preservation review and owned-service cleanup | Split review and `git diff --cached --check` passed. All original source/test files and both unrelated files match the original snapshot byte-for-byte; all six unrelated V1 planning paragraphs remain intact and unstaged. Original snapshot/manifest retained under `.cache/v2-batch-2-finalize/`. Owned jar stopped; identity/label-checked database stopped with exit 0, OOM false, restarts 0, volume retained. `.cache/v2-batch-2-finalize/cleanup.log`. Existing manual app/database still running, app health 200 |
| NOT RUN locally in finalization | New application image build/runtime/browser suite | October 23 image results remain historical. The unchanged GitHub workflow runs current jar and image checks; inspect each PR's actual CI result rather than infer it from local tests |
| PASS | PR1 private GitHub CI, exact head `b7748bfa908743be68905a4b0b43db02f907771e` | Both [PR workflow](https://github.com/NTUT-Wilson/classroom-grouping-system/actions/runs/40834286595) and [push workflow](https://github.com/NTUT-Wilson/classroom-grouping-system/actions/runs/40834239206) completed successfully. Current standard frontend, full backend/packaging, jar browser, image runtime/browser and cleanup steps passed |

An initial sandbox frontend invocation stalled before useful results and was stopped; the reported PR1/PR2 frontend passes use elevated process access. Docker pipe access is denied in the sandbox and works outside it. The PR1 standard packaging failure is preserved, with no application test failure. The owner's existing manual-acceptance Java app and database are preserved. Runtime helpers use generated server/Node-only synthetic credentials and fresh databases on loopback port 55446, with an owned jar on 8096. The temporary POM is local verification configuration, not a repository dependency or committed change.

The first PR2 attempt reused the default build directory after restoring original source timestamps. Maven skipped main-source recompilation, leaving PR1 API classes, so lifecycle test compilation failed before tests ran. `.cache/v2-batch-2-finalize/pr2-maven-incremental.log` retains that failure. The fresh-directory rerun above compiles the actual combined sources and does not reuse that failed result.

## Review and preservation

A fresh read-only split review used the requested `gpt-6.1-sol` with medium reasoning and found no source dependency blocker. It confirmed the historical V10/authority tests require no future departure/transfer endpoint, and identified unrelated V1 planning paragraphs in three shared documents. These paragraphs, `docs/v1-interview-deployment-plan.md` and `p.txt` are excluded from the commits and preserved in the working tree. Source code and existing tests retain the originally accepted implementation; finalization changes evidence/progress and divides the shared Invitations component at the acceptance-copy hunk.

## CI and remaining gates

The private repository's existing standard Ubuntu verification workflow runs on pushes and PRs, with a 20-minute job timeout and no deployment step. Read-only account checks report a Free personal account and October Actions usage of 117 Linux minutes, gross/discount $0.702 and net $0 before these runs. The [current GitHub allowance](https://docs.github.com/en/billing/concepts/product-billing/github-actions) is $12 included compute per month for Free; the bounded planned runs fit observed remaining allowance. No billing/spending/payment/workflow settings were changed. This verifies available included compute at the checkpoint, not an enduring zero-cost guarantee.

GitHub checks provide the current remote outcome for each PR; source/evidence authoring does not establish a remote pass. Even successful private CI closes no hosted cold-start, unattended scheduling, monitoring, database/session recovery or combined quota gate. Backup/restore arrangements and final resource tuning remain deferred. The production authenticated scheduler remains 204 after bounded real due work; HTTP success alone does not prove hosted reliability. Foundation and hosting are not fully validated.
