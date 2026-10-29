# V2-1a — historical actors and current-manager authorization

On 2026-10-29 (Asia/Taipei), the owner reported manual acceptance of V2 Batch 2 complete and authorized commits, branches and the two planned PRs. This records the owner's acceptance of V2-1a, V2-1b and V2-2; it does not invent additional manual test observations. Merge and deployment are not authorized. Announcements must wait until Batch 2 is merged and remain separately authorized work.

This first PR contains V2-1a only: the forward V10 migration, historical actor integrity, current-manager authorization for request history and decisions, historical labels, and replacement of private history pages. Departure, succession and transfer commands/UI belong to the dependent second PR. V10's `Group empty` cancellation allowance prepares the schema for that dependent work without adding a departure command here. The populated V9 upgrade and authority tests use database setup rather than future product endpoints.

## Finalization verification — 2026-10-29

| Result | Check | Evidence |
|---|---|---|
| PASS | PR1-only `npm test -- --maxWorkers=1` | 182 tests across 10 files; `.cache/v2-batch-2-finalize/pr1-frontend.log` |
| PASS | PR1-only `npm run build` | Current TypeScript/Vite production build; `.cache/v2-batch-2-finalize/pr1-build.log` |
| PASS tests / FAIL packaging (environment) | PR1-only Java 25 `mvnw.cmd verify` | All 166 tests passed, zero failures/errors/skips, including real PostgreSQL 17 Testcontainers and ArchUnit. Packaging then failed to rename the standard jar while the owner's manual-acceptance Java process was still using it. This command is not a full verification pass. `.cache/v2-batch-2-finalize/pr1-maven.log` |
| PASS | Packaging recovery, `mvnw.cmd -f .finalize-verification.pom.xml -DskipTests package` | Temporary local POM differs only by build `finalName=classroom-grouping-finalize-pr1`; dependencies/source/configuration unchanged. Fresh executable jar packaged successfully without stopping the manual app. Tests were not rerun by this recovery. `.cache/v2-batch-2-finalize/pr1-package-recovery.log` |
| PASS | Fresh PR1 packaged-jar request Chromium workflows | Selected `applications.spec.ts` and `invitations.spec.ts`: 4/4 passed, one worker, zero retries, 1.2 minutes. This is not a full browser suite. Separate disposable database; `.cache/v2-batch-2-finalize/pr1-browser.log` |

The 2026-10-23 whole-batch 186/frontend, 192/backend and 25/jar/image results are historical combined evidence, not PR1-only verification. A fresh read-only split review using `gpt-6.1-sol` with medium reasoning found no dependency blocker. Original files were preserved in an ignored snapshot; unrelated V1 deployment-planning paragraphs and files remain outside these commits.

## Branch and operational boundary

PR1 branch: `codex/v2-1a-request-authority`, based on `main` at `f183ab0b10bfb9e32e888a2d8641465cca8dee55`. PR2 will target this branch so its review contains only the dependent lifecycle work. Merge PR1 first, then retarget PR2 to `main` and verify the resulting diff/checks before merging it.

Read-only GitHub account/API checks on 2026-10-29 report a Free personal account, Actions enabled, and October Linux Actions usage of 117 minutes, gross/discount $0.702 and net $0. The [current GitHub billing documentation](https://docs.github.com/en/billing/concepts/product-billing/github-actions) lists $12 monthly included compute for Free; the existing standard Ubuntu workflow has a 20-minute job timeout. The planned runs fit the observed allowance. No billing setting, budget, payment method or workflow is changed. Remote CI results must be recorded after execution; these account checks alone do not prove CI success or future zero cost.

Foundation/hosting remain open for hosted cold starts, unattended scheduling, monitoring, recovery and combined quotas; backups and final resource tuning remain deferred. Local acceptance and PR creation close none of those gates.
