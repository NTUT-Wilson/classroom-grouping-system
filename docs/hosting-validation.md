# Foundation hosting validation

**Status: preparation complete; hosted execution blocked by account access.** This is a validation checklist for the approved provisional Render Free + Neon Free direction, not a deployment record or a new product requirement. No paid service or scheduler provider is selected here. Use synthetic accounts and disposable probe data only.

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
