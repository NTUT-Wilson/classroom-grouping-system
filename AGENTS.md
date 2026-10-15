# Codex instructions — Classroom Grouping

## Authority and scope

- Continue from the current repository; inspect existing code, Git status, and uncommitted work before editing. Preserve unrelated changes. Do not restart the project or reset its databases.
- `docs/PRD.md` governs product behavior; `docs/tech-stack.md` governs approved technical direction. Use `GLOSSARY.md` for terminology and consult the relevant architecture, product-definition, and user-flow documents for context. Earlier technical-pending labels are historical; do not silently replace approved decisions.
- Implement only the slice authorized by the current user request. This file does not authorize business features, deployment, cloud provisioning, or paid execution. V1 excludes AI functionality/infrastructure.
- Follow the approved sequence, dependencies, and acceptance criteria in `docs/implementation-roadmap.md`; update its progress tracker with actual implementation and verification evidence. Roadmap approval alone does not authorize starting a slice.
- Resolve routine technical details independently. Ask before changing product requirements, recurring costs, security/privacy trade-offs, or major architecture. Flag material documentation conflicts instead of inventing a product rule.

## Repository and architecture

- `backend/`: Java 25 / Spring Boot modular monolith; use its Maven Wrapper and Boot-managed dependency baseline.
- `frontend/`: React, Vite, TypeScript with Node 24; preserve exact dependency pins and the npm lockfile. Spring Boot serves built frontend assets and APIs together; Vite is development tooling.
- Under `backend/src/main/java/com/classroomgrouping/`, preserve Identity, Courses, and Grouping ownership. Courses may depend on Identity; Grouping may depend on Courses and Identity through public `api` interfaces. Do not reach across `internal` packages. `grouping/results` is read-only and owns no authoritative business tables. `platform` holds cross-cutting adapters/security, not domain ownership.
- `backend/src/main/resources/db/migration/` contains Flyway SQL migrations. Flyway alone changes schemas; Hibernate validates. Add forward migrations rather than rewriting applied ones or using destructive clean/reset.
- Keep PostgreSQL 17 consistent across local, test, and hosted validation. Project Compose uses loopback port 5433; preserve the unrelated service on 5432. Local JDK/cache directories are ignored conveniences, not portable dependencies.

## Implementation practices

- Keep changes focused and follow existing conventions. Add abstractions or dependencies only when needed by the authorized work; verify compatible released versions and preserve pins.
- Before changing grouping writes, deadline processing, results, or exports, read the relevant transaction, concurrency, idempotency, recovery, and privacy decisions in `docs/tech-stack.md` and PRD acceptance checks. Do not substitute mocks or UI checks for database integrity and service authorization.
- Use explicit request/response DTOs and safe problem responses. Keep status reads free of domain mutations and never automatically replay a mutation after an uncertain response.
- Preserve session-backed authentication, browser CSRF protection, and separate machine scheduler authentication. Keep credentials, tokens, student identity data, and request bodies out of logs and frontend configuration. Use synthetic data for verification.
- Keep demo provisioning opt-in and idempotent. Do not overwrite credentials/history or alter fixed activity state during setup. Stop temporary services after checks without deleting persistent project data.

## Verification

- Add meaningful tests alongside changed behavior, including failure and access-denial cases. Use real PostgreSQL Testcontainers for integration/concurrency evidence, ArchUnit for boundaries, and real-backend Playwright for affected browser workflows.
- For changes affecting both sides, run `npm test` and `npm run build` in `frontend/`, then `./mvnw verify` (`.\mvnw.cmd verify` on Windows) in `backend/`. Frontend assets must be built before backend packaging. Follow `README.md` for Java selection, startup, and E2E credentials.
- Run startup, browser, and Docker checks when affected and feasible. Pure documentation changes need a content/diff review, not application test reruns. Never call a selected subset, native fallback, cached build, or historical pass a full current verification.
- The native PostgreSQL recovery mode is supplementary and must target a disposable loopback cluster. It does not clear Docker/Testcontainers or hosted validation gates. Do not silently skip missing prerequisites.

## Outstanding gates and reporting

- Track current evidence in `docs/foundation.md` and hosted checks in `docs/hosting-validation.md`. Outstanding gates are Docker's Windows socket failure/full integration and revised image checks; private GitHub CI with verified no-cost allowance; and hosted cold-start, unattended scheduling, monitoring, recovery, and combined quota validation. Recheck status before claiming a blocker is resolved.
- The ordinary scheduler intentionally returns authenticated **501** until due-work implementation is authorized. A test-only probe or reachable endpoint does not prove allocation or hosted reliability. Backup arrangements and final resource tuning remain deferred as documented.
- The user has chosen to proceed with local development while these gates remain open. Preserve and report them; do not mark Foundation or hosting fully validated, or treat local success as hosted evidence.
- Report concisely: what changed, what was verified (**PASS / FAIL / NOT RUN**, with actual evidence), application failures versus environment blockers, unresolved gaps, and decisions needed. Do not claim deployment, remote CI success, or completed features without evidence.
