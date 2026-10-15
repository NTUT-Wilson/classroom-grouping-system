# Slice 1 — Accounts and course workspace

Implemented and locally verified on 2026-10-13 under the user's Slice 1 authorization. Product behavior remains governed by the PRD; hosting and remote CI remain separate open gates.

## Delivered behavior

Provisioned teachers see assigned courses; provisioned students see enrolled courses. Both can open an authorized workspace, revisit a hash course link, reload, and sign out. Backend list/detail services resolve the persisted actor from the authenticated session and recheck the current role and relationship. Unknown and inaccessible course IDs have the same safe 404 response. Anonymous course reads require authentication. Course selection creates no activity.

The English UI uses the approved sign-in/sidebar/card/breadcrumb direction and actual session/course metadata. Initialization, loading, empty lists, read failures, expired sessions, invalid credentials and inaccessible links are distinct. Lost authentication responses trigger session reconciliation without automatic POST replay. Stale reads cannot replace a newer route. Keyboard skip navigation and narrow layouts are covered. The standalone mock prototype remains available at `/?prototype=1` and its fixtures/actions are absent from the real workspace.

Profiles/About me, grouping activity settings, membership, requests, allocation, results, exports, AI, notifications, messaging and administration remain excluded. Scheduler authentication still produces the deliberate 501 placeholder for ordinary authenticated ticks.

## Data and setup

Forward Flyway **V2__student_identity_and_courses.sql** adds Identity-owned text Student IDs/names and Courses-owned courses, teacher assignments and enrollments. UUID relationships, role constraints, foreign keys and unique pairs protect integrity. Applied V1 is unchanged; Hibernate still validates and Flyway alone changes schemas. Upgrade tests preserve existing account UUID/hash and an authenticated JDBC session across V1 → V2.

`APP_ROSTER_ENABLED=true` separately opts into the documented synthetic roster. Both `ROSTER_TEACHER_PASSWORD` and `ROSTER_STUDENT_PASSWORD` must be supplied server-side: at least 12 characters, at most 72 UTF-8 bytes. No password default is committed. See [README](../README.md) for accounts and setup. Leading-zero IDs and safe Unicode are stored exactly; spreadsheet-dangerous identity values are rejected before insertion without rewriting them.

Roster setup is one transaction. Invalid/conflicting identities, roles and course metadata roll it back. Repeat setup preserves UUIDs, password hashes, identities and existing relationships; required missing relationships are added without removing existing ones. Account setup uses a shared PostgreSQL transaction advisory lock; roster relationship writes also lock course rows in stable order, compatible with future activity-creation snapshots. No grouping/history state is touched. The independently opted-in legacy Foundation account stays identity-free and course-free unless explicitly linked by provisioning data; no role promotion or password overwrite is inferred.

Material implementation choices: minimal session DTO `{accountId, username, role}`; course DTO `{id, code, name}`; GET-only course endpoints; hash routing preserves the existing serving/security surface. No new dependencies or approved product/architecture changes were needed.

## Current verification

All checks below used the current integrated files. Scratch logs and credentials are ignored under `.cache/slice1/`; credentials are never included in reports.

| Status | Command/check | Evidence |
|---|---|---|
| PASS | `frontend/npm test` | 25 tests; reviewer independently reran the same suite successfully. |
| PASS | `frontend/npm run build` | TypeScript/Vite build succeeded before backend packaging. |
| PASS | `backend/.\mvnw.cmd verify` with Java 25 | 27 tests; zero failures/errors/skips; PostgreSQL 17 Testcontainers, ArchUnit and jar packaging. `backend-verify-final.log`. |
| PASS | Fresh and V1-upgrade migrations | Real PostgreSQL startup/schema validation; V1 account/hash/live JDBC session retained through V2. |
| PASS | Provisioning and service/resource checks | Disabled roster, repeat preservation, whole-roster conflict rollback, concurrent setup, identity safety, role/FK/unique constraints, current persisted roles, legacy empty access, teacher/student access and denied mutations. |
| PASS | `frontend/npm run test:e2e`, final jar on loopback 8082 | Eight Chromium tests, no retries: distinct teacher/student lists, direct denial, unknown links, deep-link login/reload, logout/401, actual session invalidation, lost real login/logout responses with one POST each, loading and read failures at desktop 1280 and narrow 390 widths, keyboard/no overflow. `real-browser-final.log`. |
| PASS | `frontend/npm run test:prototype` | Seven unchanged standalone mock-prototype browser checks. `prototype-browser.log`. This is regression evidence only. |
| PASS | `docker build -t classroom-grouping:slice1-review .` | Current pinned multi-stage local image. `docker-build.log`. No registry push. |
| PASS | Image runtime and `npm run test:e2e` on loopback 8083 | UID 10001; 512 MiB configured limit; health 200; anonymous scheduler 401/authenticated 501; eight Chromium tests. `image-browser.log`. OOM flag false; idle sample 287.4 MiB is not hosted performance evidence. |
| PASS | Content/diff and CI configuration review | Existing unrelated baseline preserved; no dependency/lockfile/prototype source changes. YAML parsed and six extracted Bash steps passed syntax validation; this is not remote execution. Attributable patch reverse-apply check passed. |
| NOT RUN | Private remote GitHub CI | No remote execution or verified no-cost allowance/spending controls. Workflow changes only configure roster checks and runtime-generated masked credentials. |
| NOT RUN | Hosted validation/operations | Cold starts, unattended scheduling, monitoring, combined quotas and recovery; backups and final resource tuning remain deferred. |

The first full Maven run **FAILed** with two errors in the pre-existing Foundation recovery harness (27 tests, zero assertion failures, two errors). A disposable reproduction showed Docker changing a random published port from 2767 to 2769 during container restart. The test now reserves a stable loopback binding and verifies that it remains unchanged after the real restart. The focused three-test recovery suite and subsequent full 27-test run passed. The original failure remains in `backend-verify.log`; it is not hidden or relabeled as a passing run. No Docker repair/reset or application recovery relaxation was used.

Early verification also encountered the existing app on port 8080 and an internal-only Docker test network that did not expose its published Windows host route. Verification used 8082/8083 and attached the temporary image to the normal Docker bridge. These were local harness setup issues, with no production code change.

Actual jar/image desktop and narrow screenshots were inspected: sign-in/out, loading, empty, error/denied, cards and course detail remain readable, keyboard access works and no horizontal overflow was found. Artifacts are in ignored `frontend/test-results/` and `.cache/slice1/jar-browser-artifacts/`; development-stub observations are separately marked in `.cache/slice1/frontend/observations.txt`.

## Agent evidence and review

Every spawn explicitly requested **gpt-6.1-sol / medium**, with `fork_turns="none"`. Tools returned the following task identifiers; no independent runtime model/effort metadata was exposed, so actual settings are not independently verified. Researcher/implementer descriptions of inherited settings do not supersede the recorded spawn arguments.

| Returned identifier | Role/scope | Outcome |
|---|---|---|
| `/root/research_backend` | Read-only Identity/provisioning/migrations/security/test research | Identified public actor boundary, narrow GET allowlists, V1 preservation, seed validation/locking and migration-count update. No edits. |
| `/root/research_frontend` | Read-only frontend/prototype/session/test integration research | Recommended explicit auth/course states, hash routes, reconciliation/stale-read handling and real-backend gaps. No edits. |
| `/root/implement_backend` | Backend main/test source; follow-up owned only recovery test | Delivered V2, public APIs, authorized reads, provisioning and tests. Focused 11-test PASS; confirmed recovery-harness fix and three-test PASS. |
| `/root/implement_frontend` | Real App/API/styles/unit tests | Delivered workspace/states/reconciliation; 25-test/build PASS; desktop/narrow browser development loop and observations. |
| `/root/review_security` | Fresh read-only authorization/data/provisioning reviewer; authored none | PASS, no actionable findings; inspected attributable baseline diffs and current execution logs, did not rerun backend/browser suites. |
| `/root/review_ui` | Fresh read-only UI/spec/session/test reviewer; authored none | PASS, no actionable implementation findings; independently reran frontend 25-test suite and inspected source/browser evidence/screenshots. |

One independent review cycle was used. The UI reviewer noted an obsolete documentation sentence claiming the real sign-in shell links to the prototype; confirmed and corrected in `ui-prototype.md`. No review findings were dismissed or left unresolved. Before independent review, Lead inspection also found and delegated the skip-link/hash-routing fix, with a regression test. The recovery harness issue was confirmed with reproduction and fixed before review. No product/security/privacy/cost decision was changed.

Review attribution used a saved start-of-task baseline under `.cache/slice1/baseline/` and a hash-derived changed-file manifest. Reviewers were given requirements and attributable changes, not a claim that the entire dirty HEAD diff belonged to Slice 1. Lead retained coordination, API decisions, integration and whole-slice verification.

## PR proposal and preserved workspace

Recommend **one Slice 1 PR**: **“feat: add provisioned accounts and authorized course workspaces”**.

Contents: V2 migration and public Identity/Courses services; safe opt-in roster; real course UI/authentication state handling; meaningful PostgreSQL/unit/browser tests; setup/evidence/tracker documentation; only the Slice 1 CI credential/roster additions and the necessary stable-port recovery-test correction. Validation is the current passing local evidence above; remote CI must still execute later under verified no-cost controls.

Dependency: the pre-existing Foundation/prototype/instruction/skill working-tree baseline must be settled independently as the PR base. Do not stage the whole current tree or automatically include those unrelated changes. Shared files such as README, CI, App/API and the previously untracked recovery test require inspecting their Slice 1 delta against the saved baseline. No artificial backend/frontend PR split is recommended. A scratch attributable patch/manifest is retained to aid that inspection.

The repository remains on `main` in the original workspace. No new branch, worktree or commit was made; no branch was pushed, PR opened, cloud resource provisioned or deployment performed. Temporary task jar/image/database services were stopped after verification; the previously running project app/database containers and persistent data were preserved. The local review image remains available.

Slice 1's local acceptance checks and independent review passed. The tracker records local completion; Foundation as a whole and hosted/remote-CI validation remain open. Publishing readiness still depends on choosing the preserved baseline's commit/PR scope; this report does not authorize publishing it.
