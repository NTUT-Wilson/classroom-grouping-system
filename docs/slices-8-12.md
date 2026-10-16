# Slices 8–12 batch evidence — 2026-10-16

**In progress.** This authorized local batch runs sequential slice checkpoints. See [Slice 8](slice-8.md) and the [roadmap](implementation-roadmap.md). No whole-batch completion or PR readiness is claimed.

## Original state and ownership

Started on `codex/batch-2-slices-8-12` at `0135f01`, one existing worktree. The user-owned untracked `docs/manual-acceptance-slices-2-7.md` is preserved. Exact dependency pins and applied V1–V7 migrations are preserved. No database reset, push, PR, deployment, provider provisioning or paid execution is authorized.

The revised executable prompt and living progress record are in ignored `.cache/batch-2/`. The attachment itself is preserved. Requested settings use `fork_turns="none"`: Implementer `gpt-6.1-sol` / `medium`; Researchers and Reviewers `gpt-6.1-sol` / `low`. The tool returns canonical names; it does not independently expose runtime model settings.

- `/root/research_integrity`: read-only Slice 8 integrity/schema research and Slice 9 recovery preparation.
- `/root/research_privacy_tests`: read-only privacy/lifecycle/test evidence research and upcoming results/export planning.
- `/root/implement_slice8`: due-work implementation, V8 migration and 13 PostgreSQL preparation tests.
- Lead: integration, CI contract expectation, docs, full checkpoint/browser verification and review coordination.

## Current evidence

- **PASS:** frontend `npm test`, 136 tests/nine files, followed by `npm run build` on unchanged production UI.
- **PASS:** Docker engine 29.7.2 accessible outside the filesystem sandbox. The initial sandbox pipe denial is retained as an environment restriction; no Docker repair/reset was performed.
- **PASS:** focused final-source `AllocationPreparationIntegrationTest`, 13 tests, zero failures/errors/skips, PostgreSQL 17.11 Testcontainers, Java 25, 44.043 seconds, completed 15:49:31 +08. Initial 12-test run also passed; the final run added the concurrent header/placement append regression.
- **Pending:** full Maven verification, packaged-jar browser checkpoint and fresh independent Slice 8 review. Slices 9–12 remain unstarted.

The project PostgreSQL on loopback5433 and unrelated PostgreSQL5432 are preserved. The session owns isolated `classroom-batch2-verification-db` on loopback55436 and fresh database `batch2_slice8`; no app process is started yet. Only session-owned services will be stopped, without deleting volumes or data.

## Operational limits

Historical batch evidence remains in [Slices 2–7](slices-2-7.md). Local success does not establish private GitHub CI/no-cost allowance or hosted cold-start, unattended external scheduling, missed-run monitoring, recovery or combined quota validation. Backups and final resource tuning remain deferred. Foundation/hosting are not fully validated.

## Slice 8 checkpoint complete

On2026-10-16: frontend136/build; focused13 PostgreSQL preparation tests; corrected full Maven134 tests,zero failures/errors/skips,5:24; all22 packaged-jar Chromium workflows,zero retries,2.5min; production scheduler tick saved a complete mapping without assignments; three refreshes preserved domain digest and concealed memberships; fresh independent `/root/review_slice8` found no actionable defect. See [Slice8](slice-8.md) for retained initial failures and evidence. Whole-batch image/prototype and hosted gates remain separate. Source freeze/review matched this checkpoint; Slice9 is next.

## Slice 9 checkpoint complete

Slice9 adds forward V9, durable assignment stages and atomic saved-plan completion without manager appointment. Current frontend136/build; focused24 recovery tests; final full Maven146 tests,0 failures/errors/skips,6:00,16:29:26+08;4 selected packaged-jar cutoff workflows,0 retries,1.3min; actual production tick complete participant coverage, managerless system-only groups and read-only refresh digest all passed. These4 selected workflows are not the complete browser suite. Explicit V8 PREPARED-plan upgrade preservation is included in the25 allocation tests. See [Slice9](slice-9.md).

Retained failures: sandbox Docker discovery before assertions; first full145 run failed3 old SELECT* activity-row comparisons due solely to new finalized_at=null. Original fields remain exactly compared and new null field separately asserted. Fresh independent `/root/review_slice9` and a test-only follow-up found no actionable defect/weakening; no reviewer tests/browser were claimed. Lead owns execution evidence. `/root/implement_slice9` requestedgpt-6.1-sol/medium/forknone owned worker,V9,recovery/migration tests; no frontend edits. Slice10 follows after this completed gate.

## Slice 10 checkpoint complete

Authorized final results are complete locally: assigned teacher whole roster without About me, enrolled fixed student own group only, coherent finalized snapshot and safe503 for malformed state; lifecycle and English read-only result feedback are connected. Current frontend161 tests (one worker) and build, full Java25 Maven155/0failure/error/skip8:26, all23 packaged-jar Chromium workflows with one worker/zero retries3.0min, plus built-in-browser state/long-content/390px/keyboard inspection passed. Lead inspected actual integrated desktop and narrow screenshots. See [Slice10](slice-10.md) for initial resource timeout, test compilation and locator correction evidence.

Delegation: `/root/implement_slice10_backend` and `/root/implement_slice10_frontend` authored bounded backend and frontend/fixture work; resumed `/root/finish_slice10_ui` completed UI, inspected eight browser states and fixed confirmed four-source FINALIZED precedence with regression tests. `/root/research_acceptance_matrix` mapped source coverage/gaps without executing checks. Fresh `/root/review_slice10` found no actionable defect, source review only. Settings remain the requested roster above. User manual document is untouched; temporary app27324 stopped, fresh databases10/10b preserved. Slice11 follows this completed gate.

## Slice 11 checkpoint complete

Teacher-only finalized CSV uses the coherent authorized roster snapshot and shared public Identity safety policy, rejecting whole unsafe/incomplete exports before attachments without rewriting values. Exact three columns, textual IDs, numeric groups, RFC4180/CRLF/UTF8 BOM and explicit full-body download/retry are verified. Frontend178/full one-worker/build, focused20 and full Maven163/0failures/errors/skips5:04 passed; one selected packaged result/CSV browser journey passed31.2s with actual bytes/repeats/post-final edits/network failure+retry. It is not a complete browser-suite claim. See [Slice11](slice-11.md).

`/root/implement_slice11_backend` owned backend formatter/HTTP/shared safety/tests; `/root/implement_slice11_frontend` owned download API/UI/tests/E2E and its built-in browser inspection. Fresh source-only `/root/review_slice11` found no actionable findings. Requested settings match the roster above. No migration/dependency/storage service was added. Temporary app14072 stopped and database11 retained. Slice12 complete jar/image/prototype/runtime acceptance is next; hosted/remote gates remain open.
