# Foundation hosting validation

**V2 technical design checkpoint (2026-10-20):** [Approved direction](v2-architecture.md) is one application instance, external generation/embedding behind small HTTP adapters, PostgreSQL 17/pgvector exact hybrid retrieval, separate Assistant schema activation, and bounded in-memory admission with restart resets. These are design selections, not deployed resources or validated provider/account capacity. The core-first [roadmap](v2-implementation-roadmap.md) requires a joint free-generation/embedding comparison covering initial indexing, updates, rebuilds, questions, recovery and combined core quotas before provider approval. No account, external AI call, installation or hosted/CI validation occurred here; existing hosted/private-CI and backup/tuning gates remain unchanged.

**2026-10-19 planning/evidence boundary:** [V2 architecture](v2-architecture.md) and [roadmap](v2-implementation-roadmap.md) add future free-provider, vector-storage, resource-isolation, and combined-quota gates; no provider/account or hosted execution was selected/provisioned by those documents. [Timezone verification](timezone-amendment.md) records the latest attempted local check, including Docker pipe absence outside the sandbox; older Docker availability entries below are historical. All hosted/private-CI gates remain open, and no current hosting/provider claim is inferred from documentation.

**Status: preparation complete; hosted execution blocked by account access.** This is a validation checklist for the approved provisional Render Free + Neon Free direction, not a deployment record or a new product requirement. No paid service or scheduler provider is selected here. Use synthetic accounts and disposable probe data only.

Local implementation now includes durable allocation, final results and CSV; see [Batch2 evidence](slices-8-12.md). The dated provider/access observations below remain historical and do not establish current account allowances or hosted suitability.

## Current access and limits

On 2026-10-13, Render and Neon integrations were discoverable but not installed/connected. No Render/Neon credential environment variables were available. GitHub was subsequently connected and confirmed that the configured repository is private. The current committed baseline has no returned commit-status entries or PR-triggered workflow runs; the latter lookup does not cover push-triggered runs. The revised files are local and have no remote CI result. Private-runner included usage/spending controls must be verified before triggering CI under the no-cost constraint.

Rechecked official provider documentation:

- [Render Free](https://render.com/docs/free): idle services sleep after 15 minutes and can take about one minute to wake. Each workspace shares 750 free instance hours/month. Bandwidth/build overages can be billed when a payment method is present; without one, affected services/builds are suspended. External-database traffic can also cause suspension. Inspect the actual workspace's remaining allowances and billing controls before provisioning.
- [Neon plans](https://neon.com/docs/introduction/plans.md): the live Markdown document retrieved on 2026-10-13 lists 100 CU-hours/project/month, 1 GB PostgreSQL storage/project, 5 GB public network transfer/project, and scale-to-zero after five minutes for Free. Older indexed pages list different storage limits. Confirm the selected account's actual plan and allowances; do not substitute indexed historical values for its dashboard.
- [Commons CSV release](https://commons.apache.org/proper/commons-csv/download_csv.cgi): 1.14.1 is a released Java-8-or-later baseline. It is pinned in the backend; CSV business behavior remains unimplemented.

These limits are provider statements, not proof that this application's workload fits. An hourly database cleanup or frequent scheduler query can wake compute even when there are no students using the app. Connection keepalive is disabled and minimum idle connections are zero, but usage must still be measured together.

## Execution checklist once accounts are connected

1. Inspect account plan, existing shared usage, region availability, PostgreSQL 17 support, and controls that prevent charges. Stop if free-only operation cannot be ensured. Do not upgrade a tier or attach paid infrastructure.
2. Build an isolated validation artifact using the existing test-only durable `DueWork` probe. Keep it separate from the normal application image and use a disposable Neon database. The normal scheduler's authenticated 501 response cannot count as completed work. Expose neither real student data nor credentials in the artifact or logs.
3. Configure one non-root application instance, production Secure/HttpOnly/host-only/SameSite=Lax cookies, database TLS, environment-supplied credentials, and a dedicated scheduler secret. Check health and authentication without exposing sensitive management endpoints.
4. Let both app and database genuinely sleep, with browser traffic and local timers absent. Have the independently hosted scheduler invoke the authenticated POST. Verify a committed probe execution in PostgreSQL, not an HTTP loading page or merely a health response.
5. Exercise missing/wrong credentials, browser-session rejection, secret rotation, duplicate invocations, request timeouts/lost responses, app restarts, and database outages. Confirm the same durable probe is recovered exactly once after an outage.
6. Check session continuity, expiry, CSRF renewal, and logout across ordinary cold starts. Incompatible software upgrades still require explicit compatibility testing; ordinary restart evidence is insufficient.
7. Verify that an independently running monitor notices failed and missed invocations while the app is asleep. Document who receives actionable failures and how to investigate them. This is operational monitoring, not a teacher progress feature.
8. Measure total app memory, cold-start duration, database compute/storage/network, session writes/cleanup, scheduler traffic, and shared Render allowances over a documented observation period. Record actual usage and headroom without inventing a workload or uptime promise.
9. Record results, provider/cadence configuration, limitations, and teardown. If free hosting fails, present alternatives before any paid commitment. Resolve backup cadence, retention, provider recovery, encrypted storage, and costs only when concrete hosting evidence exists.

## Available local evidence and its limits

`FoundationRecoveryTest` defaults to isolated PostgreSQL Testcontainers to check startup catch-up without browser traffic, session/connection recovery after a real database restart, and production cookie defaults. An explicit native mode runs the same three tests on a disposable real PostgreSQL 17 instance when Docker is unavailable; it never silently skips or switches to an in-memory database. The original integration suite still requires Docker. Existing tests cover CSRF, logout/expiry, repeated authenticated triggers, and ordinary application restart.

CI is configured to check the image with a 512 MiB memory limit, non-root identity, API rejection, and a real-backend Chromium smoke test. The revised container checks have not executed: Docker Desktop fails during startup on an inaccessible internal `sailor-ingest.sock` file. A narrowly scoped stale-socket cleanup also failed with a Windows file-access error. No containers/volumes or database data were reset. A future memory sample will not constitute a Render performance simulation. Full feature workloads, hosted cold starts, provider quotas, encrypted backup/restore, and actual allocation recovery remain separate gates.

Remote CI must pass for the actual revised commit once GitHub access is available. Local passing tests cannot be relabeled as a GitHub-hosted result.

## Slice 1 local update — 2026-10-13

Docker is now available outside the sandbox. Current local full Maven verification passed all 27 tests, including actual Testcontainers restart/session recovery. The revised image passed local non-root (UID 10001), 512 MiB limit, health/security/501-placeholder, and eight Chromium workflow checks. The earlier Docker/socket findings remain historical; no broad Docker repair was performed by Slice 1.

These local results do not satisfy any hosted checklist item or private remote CI gate. Hosted cold starts, truly unattended scheduling, monitoring, combined quota/recovery checks, backup arrangements, and final resource tuning remain **NOT RUN/deferred**. No external authentication, provider provisioning, paid execution, remote CI run, or deployment occurred.

## Batch 2 local update — 2026-10-16 (implementation in progress)

Docker engine 29.7.2 is currently reachable outside the sandbox. The older socket startup failure remains historical; the current sandbox named-pipe denial is an environment permission boundary. Production durable deadline preparation is being implemented and verified locally; see [Slice 8](slice-8.md) and [batch evidence](slices-8-12.md). The ordinary authenticated scheduler contract is changing from placeholder 501 to 204 for a bounded processing invocation. HTTP success does not prove every activity completed, unattended execution, or hosted reliability.

No hosted checklist item or private GitHub CI gate is satisfied by this work. Account access, no-cost allowance, hosted cold starts, external scheduling, monitoring, recovery and combined quotas remain unverified; backup arrangements and final resource tuning remain deferred. No cloud resource, provider configuration, remote CI run, paid execution or deployment was performed.

Local Slice9 now verifies real saved-plan allocation and atomic finalization, including actual disposable PostgreSQL outage/restart and startup recovery. [Slice9](slice-9.md) records current full146-test and selected browser evidence. This replaces only the earlier local placeholder limitation; no hosted checklist item, unattended external service, backup restore, remote CI or quota gate is satisfied.

Local Slice10 adds complete authorized results: frontend161/build, full155-test backend and23 packaged-jar browser workflows passed; see [Slice10](slice-10.md). These local results do not satisfy hosted/remote CI gates, and current batch image/export acceptance remains pending.

## Current Batch 2 local verification — 2026-10-16

[Slice12](slice-12.md) records current frontend178/build, full Maven163 real PostgreSQL/ArchUnit tests, complete jar23/image23 Chromium workflows and prototype7. Current image build/runtime passed UID10001/configured512MiB/health200/anonymous401/authenticated production204/no OOM/restarts, with bounded281.8–326.9MiB samples. Separate fresh local jar/image databases and retained prior data were used; temporary owned services stopped. These are local functional and runtime results, not a provider simulation, peak-load proof, unattended hosted service or remote CI result.

Every hosted checklist item and the private GitHub no-cost allowance/actual CI run remain open. External scheduler/provider/cadence, monitoring/missed-run detection, combined quota/recovery validation, backup/restore arrangements and final resource tuning need the existing separate follow-up/authorization; no cloud resources, paid execution, push/PR or deployment occurred.
