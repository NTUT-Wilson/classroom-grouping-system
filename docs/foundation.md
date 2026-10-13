# Initial Project Foundation

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
