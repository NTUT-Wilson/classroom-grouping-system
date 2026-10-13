# Classroom Grouping System — V1 Technical Stack and Design

## Status and authority

**Status:** Technical direction TD1–TD34 approved on 2026-10-13, including the revised TD7, TD8, and TD26, conditional TD25 and TD30, and incremental validation clarification for TD34. Approval establishes the design direction; it does not establish implementation correctness or hosting suitability.

The [confirmed PRD](PRD.md) remains the source of truth for product behavior. Read this document alongside [architecture.md](architecture.md), [product-definition.md](product-definition.md), [user-flows.md](user-flows.md), and [GLOSSARY.md](../GLOSSARY.md). Their product rules and terminology remain unchanged. Their earlier technical-design-pending labels describe the product-review baseline; this document records the subsequently approved technical choices.

**Confirmed** means an approved technical choice, subject to implementation validation. **Provisional** means a candidate requiring hosting validation. **Deferred** means a setting, provider, version pin, or operational arrangement to resolve later within the approved direction.

This is a documentation deliverable only. No application code, project initialization, dependency installation, infrastructure provisioning, or deployment is authorized by this document. V1 excludes AI functionality and AI infrastructure.

## 1. Selected technologies

| Area | Approved selection | Version and validation status |
|---|---|---|
| Frontend | React, Vite, TypeScript | Compatible stable versions and Node.js LTS build tooling to be pinned during Foundation. |
| Backend | Eclipse Temurin Java, Spring Boot | Java 25 LTS; Spring Boot 4.1.1 approved baseline. |
| Backend build | Maven Wrapper | Wrapper version pinned during Foundation; Spring Boot manages compatible dependency versions. |
| Database | PostgreSQL | Supported major version shared by local, test, and selected hosted environments; exact version deferred. |
| Persistence | Spring Data JPA, Hibernate; targeted SQL where appropriate | Boot-managed versions; SQL remains explicit for operations needing precise control. |
| Migrations | Flyway SQL migrations | Boot-compatible version; sole schema migration mechanism. Hibernate validates the schema. |
| Authentication | Spring Security, local accounts, server-side sessions | Boot-managed versions; no registration or password-recovery UI. |
| Session persistence | Spring Session JDBC in existing PostgreSQL | Session tables managed through Flyway; database usage must pass Neon Free quota validation. |
| CSV | Apache Commons CSV | Stable compatible release pinned during Foundation; no snapshot dependency. |
| Backend tests | JUnit, Spring test tooling, PostgreSQL Testcontainers | Boot-compatible tooling; Docker-compatible local and CI runtime. |
| Boundary tests | ArchUnit | Test dependency; package ownership and dependency rules verified automatically. |
| Frontend tests | Vitest, React Testing Library, Playwright | Compatible stable versions pinned; Playwright critical workflows use the real backend. |
| Packaging | One non-root Docker image | Contains Spring Boot and built React assets; pinned build dependencies and base images. |
| Operations | Spring Boot Actuator, structured logs, correlation IDs | Minimal health exposure; sensitive details and other management endpoints restricted. |
| CI | GitHub Actions, standard Linux runners | Test and build verification; deployment remains separately authorized. |

Java 25 and Boot 4.1.1 are approved baselines, not claims that dependencies are already installed or tested. Routine compatible patch/version pins will be verified before installation. A blocker requiring a core technology or architectural replacement must be presented for approval.

## 2. Architecture and module ownership — TD26, TD28

V1 is a modular monolith: one Spring Boot application instance initially, one PostgreSQL database, and one browser-facing HTTPS origin. Spring Boot serves both the compiled React application and REST API. Vite is development/build tooling rather than a deployed frontend server.

| Module | Responsibility | Authoritative data ownership |
|---|---|---|
| Identity | Authentication, credentials, roles, persistent sessions, generic security. | Accounts, credential hashes, roles, sessions, student names and institutional Student IDs. |
| Courses | Course access, teacher assignments, enrollment, course-specific profiles. | Courses, teacher assignments, enrollments, About me. |
| Grouping | Activity lifecycle, team formation, deadline execution, allocation, recovery, finalization. | Activities, fixed participants, groups, manager authority, memberships, request attempts, idempotency receipts, allocation plans, retry and finalization state. |
| Results subpackage within Grouping | Authorized final-result projections and CSV formatting/delivery. | No separate authoritative business tables; read-only access to finalized state. |

Allowed dependencies are **Courses → Identity** and **Grouping → Courses, Identity**, through explicit public service interfaces. Identity does not call domain modules to determine course or grouping permissions. Results must not invoke grouping mutations or write grouping state. ArchUnit checks acyclic dependencies, public boundaries, and Results' read-only separation.

Enrollment belongs to Courses; the activity participant snapshot belongs to Grouping. They represent different facts rather than shared ownership. Grouping alone owns finalization; a final roster is a projection of completed memberships, not a separately finalized Results record.

The rejected four-module alternative made Results independent despite having no independent state. Keeping it as a read-only subpackage retains privacy/export separation with fewer service boundaries and less transaction coordination. No microservices, runtime module framework, event bus, or independently deployed worker is required.

## 3. Relational model and integrity — TD2–TD4

The following are conceptual entities and relationships, not final SQL table or class names.

| Record | Relationships and guarantees |
|---|---|
| Account and student identity | Seeded login identity and roles; institutional Student ID stored as text, preserving exact values and leading zeros. |
| Course, teacher assignment, enrollment | Relationships reference accounts and courses; duplicate assignment/enrollment pairs prohibited. |
| Course introduction | One About me per student/course; owned by that student, separate from seeded identity fields. |
| Grouping activity | References one course; unique course reference enforces zero or one activity per course. Stores instructions, deadline, finalization state. |
| Activity participant | Immutable creation-time enrollment snapshot; unique activity/student pair, distinct from later course enrollment. |
| Group | References activity; fixed numeric ordinal and capacity, with one group per capacity-list entry. Capacity is an integer of at least two. |
| Membership and manager authority | Membership links a participant to a group in the same activity; unique activity/student membership. Manager authority links to that group's member. |
| Application attempt | References applicant participant and target group; one pending attempt per applicant/group pair; completed attempts retained. |
| Invitation attempt | References sending manager, recipient participant, and group; one pending invitation per group/recipient pair; completed attempts retained. |
| Allocation plan and placements | One immutable plan per activity; complete remaining-student mapping, with unique planned placement per participant. |
| Retry state and command receipt | Durable execution/retry state and uniquely scoped idempotency keys; recorded success agrees with committed mutations. |

Foreign keys, unique constraints, checks, and partial unique indexes protect referential integrity, same-activity relationships, membership uniqueness, valid capacities, and pending-request uniqueness. Application and invitation tables remain separate so both may coexist for the same student/group.

Group capacity, capacity totals, manager eligibility, and final-roster completeness involve multiple records. Ordinary row CHECK constraints alone do not enforce them: application transactions, explicit locks, database constraints, and integration/concurrency tests must jointly enforce these invariants. Every application write path must follow the approved synchronization strategy.

Activity creation serializes on the existing course record because no activity row exists yet. Within one transaction, recheck assigned-teacher access and absence of an activity, read and fix the all-enrolled participant list, validate the nonempty capacity layout and exact total, and create the activity and every group. The course uniqueness constraint remains a backstop. Provisioning must not race changes to the source enrollment while establishing that snapshot. Exact SQL and constraint definitions remain implementation work.

## 4. Transactions, request lifecycle, and idempotency — TD4–TD5, TD12

Grouping mutations use PostgreSQL **READ COMMITTED** transactions and an activity-row pessimistic lock. After waiting for the lock, reread authoritative membership, capacity, request, authorization, and deadline state. Different activities remain independent.

Before saving a student grouping mutation, obtain PostgreSQL `clock_timestamp()` and require it to be strictly earlier than the current deadline. Apply the same final-time eligibility rule to permitted deadline extensions. Transaction-start `now()` is unsuitable after a lock wait. Store deadline instants using `timestamptz`; enter and display them in labeled Asia/Taipei time. This implements Q55's final eligibility check, not a physical commit-before-deadline promise. [PostgreSQL time functions](https://www.postgresql.org/docs/current/functions-datetime.html)

For successful joining, one transaction establishes membership, preserves the accepted request when applicable, cancels the student's other pending requests in that activity, and cancels remaining requests to a newly full group with reason "Group full". It also records the success receipt. Empty-group first joining establishes the manager; system assignment to an empty group does not appoint one.

Submission and response paths preserve all confirmed eligibility rules: pending requests reserve no places; completed attempts are not overwritten; reapplication/reinvitation after rejection uses a new attempt; no manual withdrawal, invitation cancellation, departure, removal, or switching is added. Rejecting one request does not change its pending counterpart. Terminal accepted, rejected, system-canceled, and expired outcomes remain distinct.

Idempotency receipts are database-backed and scoped to the authenticated actor and command. Store a request fingerprint so reuse of a key with different input cannot execute another operation. The successful mutation and receipt commit together. An explicit new attempt uses a new key; replay of the original key never creates that attempt. Reauthorize replay access and return only currently permitted information.

After timeout or a lost response, reconcile committed state before an explicit retry. A failed response does not prove rollback. Read-only refresh never resubmits a mutation, restarts allocation, or selects a new plan. Receipt representation and retention are implementation details; they must not make old keys unsafe or erase confirmed request history.

Instructions and own About me edits remain permitted after the cutoff and finalization under Q61/Q52. Their narrowly scoped updates must not change deadlines, memberships, requests, capacities, participants, allocation plans, or CSV identity data.

## 5. Deadline execution and allocation recovery — revised TD7–TD8, TD9–TD11, TD33

One provider-independent Grouping service discovers and processes due activities from durable PostgreSQL deadlines and retry state. Local timers, startup catch-up, periodic reconciliation, and an authenticated external HTTP adapter call that same service. The external scheduler never chooses students or placements. Timers and browser traffic are not the source of truth.

Start with one bounded allocation worker in one application instance. Activity locks and database uniqueness protect against repeated or overlapping triggers. Worker outages do not discard the deadlines or recovery state. Persisted work is discoverable again on startup or the next external invocation; an in-memory executor or queue is not the durable recovery mechanism.

The completion sequence is:

1. Lock and reread the activity; confirm the current deadline has passed and reconcile existing completion/plan state.
2. Expire still-pending requests, preserving completed outcomes. Validate the fixed participant roster, existing memberships, and remaining capacity.
3. On initial preparation, shuffle only the remaining ungrouped participants and map them into existing empty places. Persist the complete immutable mapping before applying any assignments; enforce one plan per activity. A failed/uncertain preparation is reconciled against actual committed state before proceeding.
4. In the assignment transaction, lock and reread the activity, reuse its stored plan, apply remaining planned memberships, validate that every participant has exactly one membership and no group exceeds capacity, and mark finalization together.
5. Expose assigned-teacher full-roster access and each participant's own final-group view only after successful complete finalization. Already-grouped activities still require deadline expiry/completion validation; an empty remaining-student plan is valid.

Existing memberships are preserved, earlier applications/invitations never influence allocation, and no overflow or additional groups are allowed. Stored plans are never replaced by a reshuffle. A random seed alone is not the durable placement record. Slot enumeration and shuffle implementation will use established Java facilities and be tested; they introduce no product preferences or AI.

Persist retry attempts, next eligible retry time, and failure classification. Automatically retry transient failures with capped backoff; a failing activity must not create a tight loop that blocks others. Reconcile uncertain commits and skip completed work. Integrity faults stop automatic assignment and require operator investigation while retaining the plan. No teacher assignment/reset/retry controls are introduced.

The authoritative cutoff and expiry semantics apply even when background materialization is delayed: pending rows must not remain actionable after the deadline. Reads can derive effective expired status without performing writes. Finalizing/unavailable feedback remains read-only, exposes no partial roster or teacher progress counts, and never weakens the deadline rule. No numerical completion-latency or availability guarantee has been approved.

## 6. Security and API design — TD13–TD22

### Authentication, sessions, and browser security

- Use Spring Security local-account login with the demo-provided username/password. Use `DelegatingPasswordEncoder` with bcrypt, benchmark its work factor, and apply bounded login throttling. Store hashes rather than plaintext credentials; keep secrets outside source control and logs.
- Persist server-side sessions with Spring Session JDBC in the existing PostgreSQL database. Flyway owns the session schema. Test ordinary restart/cold-start continuity, expiry, logout invalidation, and incompatible upgrade behavior; persistence does not promise that every software upgrade preserves every session. [Spring Session integration](https://docs.spring.io/spring-boot/reference/web/spring-session.html)
- Production session cookies are Secure, HttpOnly, host-only, and SameSite=Lax. Rotate session IDs on login; invalidate authenticated state on logout. Use Vite's development proxy for same-origin API calls. Insecure cookies are permitted only in an explicit localhost development profile.
- Store CSRF tokens server-side in sessions. Fetch them through a bootstrap endpoint and send the framework token in a header for state-changing browser requests, including login/logout. Renew on application startup and after authentication changes. Failed/expired-token handling must not automatically replay grouping commands. [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- Measure session writes, expiration cleanup, connection keepalives, and deadline reconciliation together against Neon Free quotas. Tune operational cadence without extending logical session validity or weakening deadline enforcement.

### Scheduler security

The machine-trigger POST endpoint requires a dedicated strong bearer secret supplied through backend and scheduler configuration. Browser-session authorization cannot grant access. Its separate, narrowly scoped security handling is stateless; only this machine-authenticated endpoint is exempt from browser CSRF checks. Browser APIs retain CSRF protection.

Keep the secret out of the React bundle, repository, URLs, and logs; support rotation. Reject missing/incorrect credentials and disallowed methods. Authenticate before invoking due-work processing. Repeated authenticated calls are safe through the approved locks, immutable plan, receipts where applicable, and durable state; secret authentication alone is not an idempotency guarantee.

### Authorization and privacy

Use deny-by-default service authorization plus explicit response fields. Resolve the actor from the authenticated session, not an input student ID. Recheck course assignment/enrollment, activity participation, ownership, manager authority, and current visibility rules on direct resource access as well as lists.

Before the cutoff, participants may inspect permitted group membership/manager information. After it, course peer profiles remain readable but cannot expose other students' final assignments. Students see only their own finalized group; assigned teachers see the complete roster only after finalization. Applications remain private to applicant/target manager, invitations to sender/recipient, including every completed attempt. No teacher live-progress, assignment, or request-review access is added.

Positive and negative access tests cover actor, relationship, resource, and lifecycle phase, including guessed resource IDs, stale sessions, and alternate API paths. Results code must not serialize unrestricted persistence entities or leak memberships through peer profiles, request history, errors, or idempotency replay.

### REST, validation, and history

Use JSON REST contracts with explicit request/response DTOs and command-specific mutations. Status reads have no domain side effects. Controllers validate structure; services authorize and enforce current domain rules. Routine endpoint, DTO, and method names remain implementation details.

Use RFC 9457 problem responses with stable application codes and field feedback. Distinguish authentication, permission, malformed/invalid input, lifecycle/capacity conflicts, and temporary failures; apply consistent handling to security filters as well as controllers. Do not expose internal exceptions, SQL, secrets, or private resource details. [Spring error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

Enforce strict numeric/types validation and overflow-safe capacity totals; do not silently coerce fractional capacities. Count valid Unicode code points consistently in Java, TypeScript, and database validation: About me permits 0–500; required nonblank instructions permit at most 5,000. Combined emoji may contain several code points. Preserve original text and render it as escaped plain text, without HTML execution or silent normalization.

Use cursor pagination for authorized request history, ordered by immutable creation time and a unique attempt identifier, supported by indexes. Bounded response pages are not an aggregate pending-request cap and do not delete history. Recheck authorization for every page. Pagination is not a promise of one unchanged snapshot across concurrent browser requests.

## 7. CSV export and safety — TD23–TD25

The assigned teacher's export contains exactly **Group name, Student ID, Student name**, in that order, one row per fixed activity participant. Use numeric group-number ordering, then deterministic ascending institutional Student ID ordering. Include self-grouped and system-assigned students once each; exclude About me.

Use Apache Commons CSV with RFC 4180 quoting/escaping, comma delimiters, CRLF records, and UTF-8 with BOM. Preserve institutional Student IDs exactly as stored, including leading zeros. Treat IDs as textual identifiers throughout storage and output. Do not use spreadsheet formulas, formula wrappers, or formula-style transformations to preserve them.

Read one finalized database snapshot, verify full coverage and authorization, and prepare the complete CSV before sending the download. If composing multiple reads across public module interfaces, they must share that snapshot; a read-only repeatable-read transaction can provide it. This does not change grouping mutations' approved READ COMMITTED strategy. Do not label partial/in-progress data as final or begin a successful file response before validation completes. Repeated exports of unchanged finalized data must produce consistent content. Buffering is appropriate for classroom workloads, subject to memory validation. [PostgreSQL snapshot isolation](https://www.postgresql.org/docs/current/transaction-iso.html)

**Approved CSV security condition:** Validate seeded Student IDs and names before database insertion, then validate export cells again as defense in depth. Detect dangerous spreadsheet-formula prefixes and relevant control/locale variants. Reject the entire export with clear feedback if unsafe values are found. Never silently rewrite, truncate, omit, or prefix records with apostrophes/tabs to change the data. Blocked exports are exceptional security/data-integrity conditions requiring investigation, not a normal product workflow.

CSV quoting alone is not a universal formula-injection defense, and adding characters changes underlying values. Test the policy rather than claiming arbitrary CSV data is universally safe in all spreadsheet applications. [OWASP CSV injection guidance](https://community.owasp.org/attacks/CSV_Injection)

Export tests cover exact leading-zero IDs, Unicode names, commas, quotes, line breaks, potentially dangerous formula prefixes, whole-export rejection, complete participant coverage, and repeated content/order. Document for users that Excel may require **importing Student ID as text** to avoid automatic conversion; CSV has no column-type metadata. [Excel text import](https://support.microsoft.com/en-us/excel/keeping-leading-zeros-and-large-numbers)

CSV generation uses no durable application filesystem or separate file-storage service. Safe generated filenames, download headers, and private/no-store response handling are routine implementation details. An interrupted download can be retried without mutating grouping state.

## 8. Testing and CI — TD6, TD27, TD32, clarified TD34

Validation is **incremental**. Full feature correctness is not required before the corresponding features exist.

| Stage | Required evidence |
|---|---|
| Project Foundation | Project skeleton/build, PostgreSQL connectivity/migrations, authentication/session/CSRF basics, CI, and feasibility of unattended deadline execution under provisional hosting. |
| Feature implementation | Add relevant unit, integration, concurrency, security, and E2E tests with each implemented feature. Verify assumptions when they become testable. |
| Before V1 completion | Complete deadline/allocation, preserved-plan recovery, privacy, export, and end-to-end workflows; all confirmed PRD acceptance checks and applicable operational validation. |

Foundation's deadline feasibility exercise can use a minimal due-work probe to validate external invocation, cold starts, authentication, retry/restart behavior, and quota effects. It is not a claim that the full allocation feature exists or has passed. Likewise, initial session/CSRF tests expand into feature-specific authorization and lifecycle coverage later.

Use JUnit/Spring tests and real PostgreSQL Testcontainers for integrity, transactions, migrations, and concurrency. Use distinct database connections/transactions and coordinated competing operations; mocked repositories or an in-memory database cannot prove capacity or membership safety. Verify application constraints and database constraints, not just successful sequential calls.

Feature tests must demonstrate at least:

- Concurrent activity creation, first joining, last-place approvals/acceptances, duplicate submissions, and competing joins preserve capacity, manager, participant, and membership rules.
- Same-key retries cannot duplicate mutations; new explicit attempts preserve old history; unknown commits reconcile safely.
- Lock waits crossing the deadline cannot use stale time; extension/allocation races obey the current authoritative deadline.
- Failures before/after plan commit, assignment commit, and finalization recover the same plan without duplicates, movement, or partial final-result exposure.
- Session expiry/restart/logout, CSRF renewal, scheduler authentication, and positive/negative privacy checks cover direct API access.
- Unicode limits, cursor scope, and CSV exactness/security/consistency meet their approved contracts.

Vitest and React Testing Library cover meaningful UI behavior. Playwright exercises critical teacher/student flows against the real backend, including authentication, CSRF, uncertain outcomes, finalizing feedback, and export. ArchUnit enforces module boundaries. Avoid tests that merely mirror implementation.

GitHub Actions standard Linux runners execute appropriate backend, frontend, browser, boundary, migration, and Docker-build checks as those components are introduced. Keep fixtures isolated and production credentials out of CI; passing after a flaky retry does not prove concurrency correctness. No automatic deployment is approved. Standard public-repository runners are free; private usage and artifact storage require quota checks. [GitHub Actions billing](https://docs.github.com/en/actions/concepts/billing-and-usage)

## 9. Deployment, operations, provisioning, and recovery — TD7, TD28–TD34

### Confirmed operating direction

Develop locally first. Run one Spring Boot instance and one allocation worker initially. Build one non-root image containing backend and React assets; supply secrets through environment configuration, never frontend build variables. Preserve SPA navigation while API, scheduler, and management routes retain their actual error/security handling.

Use bounded JVM memory, thread/worker execution, database connections, and timeouts based on Foundation measurements. Account for total process memory rather than heap alone. Size for classroom workloads without inventing new product limits on participants, capacities, or aggregate requests. Preserve protected database connectivity and connection recovery; avoid background traffic that needlessly keeps free-tier database compute active.

Use redacted structured logs and correlation IDs for request/job attempts, retries, and failure classification. Do not log credentials, bearer secrets, cookies, request bodies, introductions, or roster identity data. Expose minimal health information; restrict sensitive Actuator endpoints and configure their access explicitly alongside custom security chains.

Scheduler monitoring must distinguish actual due-work execution from a hosting loading page or mere HTTP connectivity. Detect failed or missed invocations outside a sleeping backend. Operational investigation is separate from product access: there is no new teacher progress view, administrative UI, or product notification feature.

### Provisional hosting and mandatory validation

**Render Free + Neon Free + an external scheduler is a provisional portfolio candidate, not a confirmed production deployment.** The external scheduler provider and frequency are deferred. Cloudflare scheduled Workers was an evaluation candidate, not an approved provider commitment. Render Starter is not approved.

Render Free can sleep without inbound traffic; HTTP requests wake it, with startup delay. Free quotas can suspend service, and the filesystem is ephemeral. An external trigger can address ordinary sleeping, but cannot guarantee execution while a service is suspended or unavailable. These constraints require evidence from unattended cold-start/retry/recovery tests, not keep-alive assumptions. [Render Free limitations](https://render.com/docs/free)

During Foundation, validate the proposed hosting setup's unattended execution feasibility with zero student traffic, along with startup/resources, database connection recovery, initial session/CSRF behavior, CI, scheduler security, failure detection, and combined free-tier usage. Later features add actual allocation/recovery and workload evidence. Recheck current provider quotas and terms at validation; no free tier is presumed unlimited or permanently suitable.

Local execution is automatic only while the computer/application runs; startup catch-up does not make an offline laptop an unattended hosted service. A free-hosting failure must lead to an alternatives proposal before committing to paid infrastructure. Preserve the provider-independent application design and all PRD requirements.

### Provisioning and releases

Use an explicit, opt-in, idempotent demo provisioning process for accounts, courses, assignments, and enrollments. Validate seeded export identities before insertion. Repeated setup must not reset activities, overwrite history, change established participants, or replace allocation plans. There is no registration, enrollment-management, or reset UI.

Flyway is the only schema migration mechanism; disable competing automatic schema creation/initializers and let Hibernate validate. Test empty-database and upgrade paths as migrations are added. Prefer forward-compatible changes; do not silently rewrite applied migrations, run destructive clean/reset, or automatically downgrade schema. Roll back an application artifact only when compatible with the current data/schema; otherwise use a tested forward fix. [Spring database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)

### Conditional backup and restore direction

Keep portfolio backup/recovery simple and cost-effective: use provider recovery where available plus encrypted PostgreSQL backups outside the application filesystem. Include memberships, request history, allocation plans and placements, finalization and retry state, and the supporting schema/data needed for a coherent restore. Do not provision paid backup infrastructure without approval.

**Backup frequency, retention, external storage provider, recurring cost, and available provider recovery window remain deferred until hosting validation.** No numerical recovery-point or recovery-time guarantee has been established.

Verify restoration in an isolated environment, including schema compatibility, relationships, immutable plans, completed outcomes, finalization, and resumed due work. Backups must not overwrite newer committed grouping state. Missing original plans cannot be replaced by new random choices: halt affected activities for investigation if the chosen plan cannot be recovered. Restoring an older backup is not proof that all later committed state survived. Session invalidation and credential/secret handling during restore must be tested as part of the operational procedure.

## 10. Approved decision register

All entries below are approved direction subject to the conditions and staged validation above. Superseded proposals are not commitments.

| ID | Final decision |
|---|---|
| TD1 | Temurin Java 25 LTS, Spring Boot 4.1.1, Maven Wrapper, Boot-managed dependencies. |
| TD2 | JPA/Hibernate with targeted SQL; Flyway owns migrations, Hibernate validates. |
| TD3 | Normalized data model, fixed participants, retained attempts, database constraints and transactional aggregate integrity. |
| TD4 | READ COMMITTED grouping transactions, activity locks, current-state and final-time checks. |
| TD5 | Durable command receipts and atomic successful mutations; safe same-key retries. |
| TD6 | JUnit/Spring and real PostgreSQL Testcontainers integration/concurrency tests. |
| TD7 | Revised: local-first; free hosting plus external scheduling provisional; no paid hosting commitment. |
| TD8 | Revised: provider-independent durable due work, interchangeable local/external triggers, no student-traffic dependency. |
| TD9 | One complete immutable PostgreSQL allocation plan before assignments. |
| TD10 | Planned assignment, completeness validation, and finalization commit atomically. |
| TD11 | Durable capped-backoff transient retries; integrity faults halt; original plan retained. |
| TD12 | PostgreSQL clock_timestamp(), stored instants, Asia/Taipei deadline entry/display. |
| TD13 | Spring Session JDBC; Flyway session schema; Neon usage validation. |
| TD14 | DelegatingPasswordEncoder/bcrypt, secure seeded credentials, benchmarked hashing and bounded login throttling. |
| TD15 | Secure/HttpOnly/host-only/SameSite=Lax cookies; session rotation and logout invalidation. |
| TD16 | Session-backed CSRF headers for browser mutations, login/logout, and token renewal. |
| TD17 | Dedicated scheduler bearer secret; no browser-session authorization; narrow machine-only CSRF exemption. |
| TD18 | Deny-by-default service/resource authorization and privacy-safe responses; positive and negative access tests. |
| TD19 | Explicit JSON REST contracts and mutations; side-effect-free status reads. |
| TD20 | RFC 9457 problems with stable codes, field feedback, and safe security/error responses. |
| TD21 | Strict types/limits, consistent Unicode code-point counting, escaped plain text. |
| TD22 | Indexed, authorized cursor history pagination; no aggregate cap or deletion. |
| TD23 | Commons CSV, RFC 4180 quoting, CRLF, UTF-8 BOM; exact IDs, no formulas, Excel text-import guidance. |
| TD24 | Complete finalized snapshot, pre-delivery generation, repeatable export content. |
| TD25 | Conditional: pre-insertion seed validation plus export checks; reject whole unsafe export explicitly, preserve original values. |
| TD26 | Revised: Identity, Courses, Grouping; read-only Results subpackage; explicit boundaries and ArchUnit checks. |
| TD27 | Vitest, React Testing Library, Playwright critical real-backend workflows. |
| TD28 | One non-root Docker image with Spring Boot and built React assets. |
| TD29 | Structured/redacted logs, correlation IDs, minimal health checks, external scheduler failure detection. |
| TD30 | Conditional: simple encrypted/provider recovery, restore tests; backup schedule/storage/retention/cost deferred, no paid provisioning. |
| TD31 | Explicit idempotent demo setup and safe forward-compatible upgrades; no automatic activity reset. |
| TD32 | GitHub Actions Linux test/build checks; deployment separate. |
| TD33 | One initial app instance/allocation worker; measured bounded runtime resources. |
| TD34 | Clarified: Foundation basics/hosting feasibility, tests alongside features, full workflow verification before V1 completion. |

## 11. Deferred details, risks, and next documentation needs

| Item | What remains unresolved |
|---|---|
| Hosting | Provider/tier suitability, scheduler provider/cadence, startup timeouts, quotas, failure detection configuration; alternatives require approval if free hosting fails. |
| Backups | Frequency, retention, external storage, provider recovery window, costs and measured restoration behavior. |
| Dependency pins | Frontend/Node tooling, database major, Maven Wrapper, unmanaged test/CSV libraries, image digests; compatibility verification before installation. |
| Runtime configuration | JVM/native memory, thread/worker/pool bounds, session cleanup/expiry settings, login throttling, retry timing, network/database timeouts. |
| Persistence implementation | Physical schema/constraints/indexes, manager linkage, migration SQL, receipt lifetime, retry-state representation, coherent read implementation. |
| API/UI implementation | Endpoint and DTO names, cursor encoding/page sizes, error-code catalogue, exact filenames/headers, text-boundary feedback and timezone formatting. |
| Operational expectations | Measured classroom workload and processing delay; no invented load, uptime, latency, or backup guarantee. |

Major remaining risks are free-tier unattended execution, combined database usage from sessions/polling/keepalives, memory/transaction duration for allocation and buffered export, detection of missed scheduling, and recoverability of original plans. Feature-level correctness remains unproven until implemented and tested. Blocked CSV exports must remain exceptional and explicit.

Detailed schema/constraint and concurrency/recovery test plans may be documented separately when implementation planning needs them. A deployment/restore runbook should follow hosting validation, where providers, cadence, retention, and costs can be concrete. These are future documentation needs, not newly approved infrastructure or product scope.

No additional product decision is introduced. Preserve the confirmed PRD and glossary throughout subsequent Foundation and feature work.
