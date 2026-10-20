# Classroom Grouping

Local implementation of the approved V1 modular monolith. React + Vite + TypeScript, Java 25 / Spring Boot 4.1.1, PostgreSQL, Flyway, and Spring Security with JDBC-backed sessions.

The [project-wide assumptions](docs/project-assumptions.md) require English-only operation and `America/New_York` deadline entry/display across all versions. Daylight-saving gaps and repeated wall times are rejected; stored deadline instants remain unchanged. See [timezone verification](docs/timezone-amendment.md) for current checks and blockers.

V2 requirements are defined in the [V2 PRD](docs/v2-PRD.md), [RAG architecture](docs/v2-architecture.md), [user flows](docs/v2-user-flows.md), and [implementation roadmap](docs/v2-implementation-roadmap.md). They cover group departure/manager succession and transfer, announcements, and a separately gated free-only course assistant. They are planning documents; V2 business features are not implemented and no implementation slice/provider/deployment is authorized by their completion.

Slice 1 adds provisioned teacher/student course lists and authorized course workspaces to the existing authentication. [Slice 2](docs/slice-2.md) connects profiles/About me; [Slice 3](docs/slice-3.md) adds activity creation; [Slice 4](docs/slice-4.md) adds instruction edits, deadline extensions and safe status reads. [Slice 5](docs/slice-5.md) adds student discovery and deliberate empty-group joining. [Slice 6](docs/slice-6.md) adds private applications, manager decisions and retained history. [Slice 7](docs/slice-7.md) adds invitations and combined request cleanup. [Slice8](docs/slice-8.md) adds durable deadline preparation and saved plans. [Slice9](docs/slice-9.md) adds atomic assignment/finalization and preserved-plan recovery. [Slice10](docs/slice-10.md) adds authorized finalized teacher rosters and student own-group results. [Slice11](docs/slice-11.md) adds safe teacher-only finalized CSV export; administration is outside this V1 scope. See [the PRD](docs/PRD.md), [approved technical design](docs/tech-stack.md), [Slice 1 evidence](docs/slice-1.md), and [foundation verification record](docs/foundation.md).

## Prerequisites

- Temurin Java 25 on `JAVA_HOME` / `PATH`.
- Node.js 24 LTS and npm.
- Docker with a running Linux-container engine, for local PostgreSQL and Testcontainers.

This setup downloaded a checksum-verified Temurin 25 JDK into ignored `.tools/`; it did not replace the machine's Java 17 installation. In this workspace, PowerShell can select it temporarily:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools -Directory -Filter 'jdk-25*' | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

A fresh checkout requires an installed Java 25 JDK; `.tools/` and caches are not source files.

## Run locally

From the repository root:

```powershell
docker compose up -d --wait db
```

The project's PostgreSQL 17 container is bound to **127.0.0.1:5433**, avoiding the existing PostgreSQL service on this machine's port 5432. Data persists in a project-specific Docker volume. The included database password is for loopback-only development, not a hosting credential. An optional `DATABASE_PASSWORD` override must be supplied consistently to Compose and the backend.

Install frontend dependencies and build assets:

```powershell
cd frontend
npm ci
npm run build
cd ..
```

Opt into one foundation demo account, then run the backend:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:APP_DEMO_ENABLED = 'true'
$env:DEMO_USERNAME = 'foundation-demo'
$env:DEMO_PASSWORD = 'choose-your-local-password'
cd backend
.\mvnw.cmd spring-boot:run
```

Open **http://127.0.0.1:8080/**. Spring Boot serves the built frontend and API together. The local profile is explicitly restricted to development: cookies are insecure only there, and the host process binds to loopback. Production defaults use Secure, HttpOnly, host-only, SameSite=Lax session cookies and require database environment settings.

Foundation demo setup is disabled by default. It creates only an authentication account, with a bcrypt hash. Rerunning it does not overwrite the account or password. Keep the password used on first setup; changing the environment value does not reset an existing account. This legacy account receives no inferred Student ID, name, or course access. Demo passwords must contain at least 12 characters and remain within bcrypt's 72-byte limit.

### Opt-in course workspace roster

The synthetic roster is separate from the Foundation account and disabled by default. Before starting the backend, opt in and supply both passwords on the server:

```powershell
$env:APP_ROSTER_ENABLED = 'true'
$env:ROSTER_TEACHER_PASSWORD = Read-Host -Prompt 'Synthetic teacher password (12+ characters)' -MaskInput
$env:ROSTER_STUDENT_PASSWORD = Read-Host -Prompt 'Synthetic student password (12+ characters)' -MaskInput
```

`roster-teacher` and `roster-student` access DEMO-101 (Synthetic Classroom).
`roster-other-teacher` and `roster-other-student` access DEMO-202 (Another Synthetic Classroom).
`roster-empty-student` has no enrollments. Teacher accounts use the configured teacher password; student accounts use the student password. All identities are synthetic; the stored Student IDs are text with leading zeros.

Provisioning validates the entire roster and commits atomically. Repeated setup preserves account UUIDs, passwords, identities, and existing relationships; it adds required missing relationships without removing existing ones. Conflicting roles, identities, or course metadata reject setup without partial roster changes. Setup locks course rows before relationship writes, for compatibility with future activity snapshots. No grouping activity is created. Disable opt-in flags after setup; existing accounts remain usable. No plaintext password is built into this roster.

Select a course through the cards or revisit `http://127.0.0.1:8080/#/courses/<course-UUID>`. Hash links restore after login or reload; the backend rechecks authorization on every course read. Unknown and unauthorized courses receive identical safe feedback.

Students can open the course's Classmates tab (`#/courses/<course-UUID>/classmates`), read names, exact textual Student IDs and introductions, and save their own About me for that course. Text is optional, preserved as plain text, and limited to 500 Unicode code points. Profiles contain no grouping assignments. A save whose response is lost must be checked through the read-only reconciliation button before another explicit save.

### Isolated batch browser fixtures

`APP_BATCH_FIXTURE_ENABLED=true` (or `--app.batch-fixture.enabled=true`) separately opts into synthetic BATCH courses for isolated acceptance workflows. Supply `BATCH_FIXTURE_PASSWORD` server-side (12+ characters, at most 72 UTF-8 bytes). Accounts are `batch-teacher` and `batch-student-1` through `batch-student-8`. BATCH-101/505/606/707/1001 have eight students; BATCH-202/404/405/506 have the first two; BATCH-607/708/1002 have the first four; BATCH-303 has no students. Setup is additive and idempotent, preserves existing identities/passwords/history, and creates no activities. Use a disposable local database for acceptance journeys and disable the flag after setup. The original DEMO courses remain separate.

For frontend hot reload, use a second terminal:

```powershell
cd frontend
npm run dev
```

Open Vite's displayed loopback URL. Its `/api` proxy reaches the backend without enabling permissive CORS. Rebuild `frontend/dist` before packaging a new same-origin application jar.

## Verify

```powershell
cd frontend
npm test
npm run build
cd ../backend
.\mvnw.cmd verify
```

Backend verification requires Docker and uses isolated, automatically cleaned PostgreSQL Testcontainers; it does not use or reset your development database. Tests cover fresh/upgrade migrations, provisioning rollback/repetition/concurrency, role/course authorization, real JDBC sessions, CSRF, expiry/logout/restart behavior, module boundaries, scheduler authorization, and a **test-only** durable deadline probe. The probe does not establish allocation correctness. Separate production tests cover immutable saved plans, database-clock races, capped retries/integrity halt, actual PostgreSQL outage/startup recovery, atomic completion, complete authorized result snapshots and safe exact CSV output.

With the built backend running against a fresh disposable database, all three opt-in setup flags enabled (`APP_DEMO_ENABLED`, `APP_ROSTER_ENABLED`, `APP_BATCH_FIXTURE_ENABLED`), and `DEMO_USERNAME`, `DEMO_PASSWORD`, `ROSTER_TEACHER_PASSWORD`, `ROSTER_STUDENT_PASSWORD`, and `BATCH_FIXTURE_PASSWORD` set in your terminal, plus the same `SCHEDULER_SECRET` supplied to the backend and Node test process:

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e -- --workers=1
```

The single-worker command runs every test while keeping multiple authenticated actors inside each journey. It fits the verified 8 GB local host; database concurrency uses coordinated independent PostgreSQL connections.

`E2E_BASE_URL` can override the default `http://127.0.0.1:8080`. Browser tests require a separate identity-free Foundation account plus the documented synthetic roster. They exercise teacher/student access, profiles, creation, settings, joining, private requests, actual cutoff, keyboard/mobile layouts, deep links and lost committed responses without replay. Creation journeys require a fresh disposable database for each complete run; retain prior history rather than resetting activities. Use separate fresh databases for jar and image runs. Read-failure tests inject transport failures around real authenticated sessions. They do not silently skip when credentials or services are missing. Result journeys invoke the production scheduler from the Node test process; never use `VITE_*` variables or browser code for its secret. Screenshot artifacts are in ignored `frontend/test-results/`.

The GitHub Actions workflow runs frontend checks, PostgreSQL-backed backend tests, real-backend Chromium smoke tests, and a Docker build. It contains no deployment step. Its execution on GitHub remains unverified until this repository is pushed and the workflow actually runs.

## Single application image

```powershell
docker build -t classroom-grouping:foundation .
docker compose --profile app up -d --wait app
```

Compose's optional app profile publishes only loopback port 8080 and uses the local profile. Set demo environment variables before starting it if sign-in is desired. It connects to `db:5432` inside Docker. The runtime image contains only the Java runtime, application jar, and built React assets and runs as UID 10001.

No cloud deployment or provider provisioning has been performed. Render Free, Neon Free, external scheduler selection, quotas, monitoring, and backup arrangements remain subject to the approved validation gates.

The remaining hosted checks and account-access requirements are recorded in
[hosting-validation.md](docs/hosting-validation.md). Scheduler HTTP success alone must never be treated as proof that allocation or hosted deadline work completed.

## Password-hashing measurement

The existing Spring Security bcrypt strength remains unchanged. To measure it
with synthetic input after compiling the tests:

```powershell
cd backend
.\mvnw.cmd -DskipTests test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/benchmark-classpath.txt
$benchmarkDependencies = (Get-Content target/benchmark-classpath.txt -Raw).Trim()
java -cp "target/test-classes;$benchmarkDependencies" com.classroomgrouping.platform.PasswordHashBenchmark
```

This prints timings and the work factor, not credentials or hashes. It is a manual
measurement, not a pass/fail performance threshold. Repeat on the chosen hosted
hardware before final runtime tuning.

The default backend tests require Docker. For supplementary recovery verification
on Windows when Docker cannot start, `FoundationRecoveryTest` also supports an
explicit native PostgreSQL mode. Point it **only at a disposable PostgreSQL 17
cluster**: the test creates foundation/probe data and restarts that cluster.

```powershell
.\mvnw.cmd -Dtest=FoundationRecoveryTest -Dfoundation.test.native=true `
  '-Dfoundation.test.jdbc.url=jdbc:postgresql://127.0.0.1:55433/postgres' `
  '-Dfoundation.test.jdbc.user=your-disposable-user' `
  '-Dfoundation.test.jdbc.password=your-disposable-password' `
  '-Dfoundation.test.pgctl=C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' `
  '-Dfoundation.test.pgdata=C:\absolute\path\to\disposable\data' test
```

These tests use the real database; they do not replace the required Testcontainers
suite or prove hosted behavior.

## Scheduler contract and configuration

`POST /internal/scheduler/tick` requires `Authorization: Bearer <SCHEDULER_SECRET>`. The secret must be at least 32 bytes when configured; with no secret, the endpoint rejects all calls. It is stateless and cannot authorize browser sessions. Its dedicated security chain is the only CSRF exemption; login/logout and other browser mutations remain protected.

The production `DueWork` implementation prepares durable allocation plans for due activities and expires pending requests. An authenticated tick returns **204 No Content** after its bounded processing invocation; this does not mean every activity completed successfully. Integrity faults halt affected work; transient failures retain durable capped-backoff retries. Slice9 adds a separate atomic assignment/finalization transaction after the immutable plan commits. Slice10 exposes only complete authorized projections: assigned teachers use `/api/courses/{courseId}/results/roster`; currently enrolled fixed student participants use `/api/courses/{courseId}/results/my-group`. Reads validate finalized coverage in a coherent snapshot and never run due-work. Teachers receive no About me, and students receive only their own group. Local timer/catch-up adapters remain disabled by default; `--app.scheduler.enabled=true` enables startup catch-up and periodic reconciliation. Foundation tests retain their separate test-only probe; production preparation has its own PostgreSQL coverage.

Runtime defaults are conservative starting points, not validated cloud budgets: JDBC pool maximum 5, minimum idle 0, keepalive disabled, connection timeout 10 seconds, session idle timeout 30 minutes, expired-session cleanup hourly, and temporary login throttling at 100 attempts per minute per remote address with bounded memory. These must be tuned against actual hosting, shared-client networks, database usage, and security measurements. Cleanup cadence does not extend logical session validity.

Database settings are `DATABASE_URL` (JDBC URL), `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Never put database credentials or the scheduler secret into `VITE_*` variables or frontend source. The default profile requires database settings; the local profile supplies loopback-only development defaults. Structured logs avoid request/credential bodies; API errors carry a correlation ID and safe problem details.

Stop local services without deleting data:

```powershell
docker compose --profile app stop
```

Stop any directly launched backend/Vite processes in their own terminal. Do not use volume removal or database reset as a normal setup/recovery step.

### Final roster CSV

After complete finalization, the assigned teacher can choose **Download CSV** in Final roster. The fresh authorized endpoint is `GET /api/courses/{courseId}/results/roster.csv`. It returns exactly Group name, Student ID, Student name, ordered by numeric group ordinal then deterministic textual Student ID, with RFC4180 comma quoting, CRLF and UTF8 BOM. Every fixed participant appears once; About me is excluded. The full snapshot and all cells are validated before a successful attachment is created. Unsafe or incomplete data rejects the whole export with safe503 feedback; values are never rewritten. Failed/interrupted downloads require an explicit retry and do not change grouping.

In Excel, import Student ID as text to preserve leading zeros: CSV contains no column-type metadata. IDs remain unchanged in the file. Downloads use private/no-store headers and no durable application file storage.
