# Classroom Grouping

Minimal local foundation for the approved V1 modular monolith. React + Vite + TypeScript, Java 25 / Spring Boot 4.1.1, PostgreSQL, Flyway, and Spring Security with JDBC-backed sessions.

The foundation includes authentication plumbing and a small sign-in shell. Course management, grouping, requests, allocation, final results, and CSV exports are **not implemented**. See [the PRD](docs/PRD.md), [approved technical design](docs/tech-stack.md), and [foundation verification record](docs/foundation.md).

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

Demo setup is disabled by default. It creates only a foundation authentication account, with a bcrypt hash. Rerunning it does not overwrite the account or password. Keep the password used on first setup; changing the environment value does not reset an existing account. No courses, enrollments, student profiles, or grouping activities are seeded yet. Demo passwords must contain at least 12 characters and remain within bcrypt's 72-byte limit.

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

Backend verification requires Docker and uses an isolated, automatically cleaned PostgreSQL Testcontainer; it does not use or reset your development database. Tests cover Flyway/JPA startup, real JDBC sessions, CSRF, expiry/logout/restart behavior, scheduler authorization, module boundaries, and a **test-only** durable deadline probe. They do not establish business-feature correctness.

With the built backend running and the same demo credentials set in your terminal:

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e
```

`E2E_BASE_URL` can override the default `http://127.0.0.1:8080`. Browser tests exercise actual same-origin sign-in, reload, and logout. They require the real backend; they do not silently skip when credentials or services are missing.

The GitHub Actions workflow runs frontend checks, PostgreSQL-backed backend tests, real-backend Chromium smoke tests, and a Docker build. It contains no deployment step. Its execution on GitHub remains unverified until this repository is pushed and the workflow actually runs.

## Single application image

```powershell
docker build -t classroom-grouping:foundation .
docker compose --profile app up -d --wait app
```

Compose's optional app profile publishes only loopback port 8080 and uses the local profile. Set demo environment variables before starting it if sign-in is desired. It connects to `db:5432` inside Docker. The runtime image contains only the Java runtime, application jar, and built React assets and runs as UID 10001.

No cloud deployment or provider provisioning has been performed. Render Free, Neon Free, external scheduler selection, quotas, monitoring, and backup arrangements remain subject to the approved validation gates.

## Scheduler contract and configuration

`POST /internal/scheduler/tick` requires `Authorization: Bearer <SCHEDULER_SECRET>`. The secret must be at least 32 bytes when configured; with no secret, the endpoint rejects all calls. It is stateless and cannot authorize browser sessions. Its dedicated security chain is the only CSRF exemption; login/logout and other browser mutations remain protected.

The production `DueWork` implementation deliberately returns **501 Not Implemented**: deadline allocation is not a foundation feature. Do not treat endpoint reachability as completed allocation. Local timer/catch-up adapters are disabled by default; the integration suite enables them with a test-only PostgreSQL probe to verify automatic invocation and safe duplicate probe processing.

Runtime defaults are conservative starting points, not validated cloud budgets: JDBC pool maximum 5, minimum idle 0, keepalive disabled, connection timeout 10 seconds, session idle timeout 30 minutes, expired-session cleanup hourly, and temporary login throttling at 100 attempts per minute per remote address with bounded memory. These must be tuned against actual hosting, shared-client networks, database usage, and security measurements. Cleanup cadence does not extend logical session validity.

Database settings are `DATABASE_URL` (JDBC URL), `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Never put database credentials or the scheduler secret into `VITE_*` variables or frontend source. The default profile requires database settings; the local profile supplies loopback-only development defaults. Structured logs avoid request/credential bodies; API errors carry a correlation ID and safe problem details.

Stop local services without deleting data:

```powershell
docker compose --profile app stop
```

Stop any directly launched backend/Vite processes in their own terminal. Do not use volume removal or database reset as a normal setup/recovery step.
