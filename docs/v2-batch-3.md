# V2 Batch 3 — announcements and core acceptance

**Owner acceptance and integration — 2026-11-05 (Asia/Taipei):** The owner explicitly accepts Batch 3 as having passed manual acceptance. This records the owner's decision; no separate hands-on observations were supplied in this checkpoint. PR [#7](https://github.com/NTUT-Wilson/classroom-grouping-system/pull/7) merged into `main` as `ff1c372` on 2026-11-05 at 01:14 Asia/Taipei. The [PR workflow](https://github.com/NTUT-Wilson/classroom-grouping-system/actions/runs/41873585460) and [post-merge main workflow](https://github.com/NTUT-Wilson/classroom-grouping-system/actions/runs/41875602104) both completed successfully for their respective heads. Earlier proposal, pending-acceptance and NOT RUN wording below describes its dated checkpoint. V2-5 and later, hosted operational validation, backups and deployment remain separate.

## Scope and current status — 2026-11-05

The owner authorized local V2-3a, V2-3b and V2-4 implementation, verification and independent review. Work starts from merged Batch 2 `main` at `a810706`. The owner-supplied post-merge CI run [41018460103](https://github.com/NTUT-Wilson/classroom-grouping-system/actions/runs/41018460103) is baseline evidence, not verification of Batch 3. No branch push, PR, merge, deployment, provider call or Assistant implementation is authorized here.

**Locally complete — ready for a PR proposal:** implementation, local automated acceptance and both independent review cycles are complete. The corrected source passed full verification on November3; November5 resumed finalization confirms unchanged application/test source, the same jar SHA256, preservation and stopped services. No new full application test run is claimed for November5. Owner manual acceptance of Batch 3 has not been reported.

Courses owns announcement content, revisions, catalog, command receipts and metadata-only outbox. Announcements use current assignment/enrollment independently of grouping activities and cutoff. They publish immediately, preserve publication ordering on edit, require revisions for edit/delete, and clear title/body on confirmed deletion. Receipt reconciliation distinguishes a committed command from later changes/deletion without replaying mutations automatically. AI remains absent.

## Transactions and retention

Announcement operations use READ COMMITTED and the course row before current relationship reauthorization. Writes/maintenance/conditional acknowledgement take the course UPDATE lock; browser reads take SHARE. The order is course, fresh identity/access, receipt, source, catalog, outbox. Provisioning uses the same course-first order. Cross-course receipt uniqueness waits are followed by fresh authorization. Successful source changes, catalog increments, receipt and newly appended event commit atomically.

Receipts bind actor/command/key to a length-prefixed SHA-256 digest of command, course, target, expected revision and exact validated inputs. They retain minimal committed ID/revision/deletion facts, never text snapshots. Keys and tombstones are retained: an old creation key cannot publish a duplicate. A course catalog revision changes on create/edit/delete. Pagination uses immutable publication time plus UUID, with actor/course-scoped cursors and current authorization on every page.

Every source mutation appends an event. Bounded compaction then removes provably older revisions for the same source under the course lock; it keeps the latest event, including deletion intent. Conditional acknowledgement uses the same lock order and joins caller transactions. There is no maximum-event-ID watermark, consumer, recurring cleanup, read-triggered cleanup, provider dependency or optional schema.

## Explicit maintenance

Maintenance privilege is shell execution plus backend database credentials. There is no web endpoint or product/scheduler permission. The dedicated entry point uses JDBC directly; it does not start Spring, a listener, provisioning, Flyway or allocation scheduling. It requires the migrated core schema.

Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` in the operator process, then run against the intended database:

```powershell
java -Dloader.main=com.classroomgrouping.courses.internal.AnnouncementMaintenance `
  -cp backend/target/classroom-grouping-0.0.1-SNAPSHOT.jar `
  org.springframework.boot.loader.launch.PropertiesLauncher --compact 100
```

The explicit bound is 1–1,000 courses per invocation, with at most 1,000 obsolete events removed per selected course. Repeated invocations drain accumulated obsolete events; they do not remove latest intents, authoritative sources/tombstones or receipts. Ordinary writes compact up to 64 obsolete events. No production maintenance run is authorized or claimed. Packaged invocation verification and measurements are recorded below.

## October 30 development checkpoint (historical)

| Result | Evidence |
|---|---|
| PASS | Node 24.16.0, installed Java 25 and unchanged dependency pins inspected. Isolated `codex/v2-batch-3` branch created. Unrelated V1 demo checkout preserved. `p.txt` never read; explicitly excluded from Docker context. |
| FAIL — environment, retained | Initial Docker Linux pipe unavailable; first default announcement Testcontainers run could not start. Docker Desktop UI inspection timed out at app approval. Desktop startup later recovered server 29.7.2; current Testcontainers rerun underway. |
| PASS — supplementary | New disposable native PostgreSQL 17 cluster on loopback 55450, dev database/app on 8098; startup/health 200. Used to unblock built-in browser development. This is not a full Testcontainers/image pass. |
| FAIL — application, retained | First native and first recovered Docker focused announcement runs each exercised 12 methods: 11 passed, malformed JSON HTTP test lacked announcement-specific safe code because Boot advice took precedence. Safe advice ordering corrected; rerun underway. |
| NOT RUN / pending | Final complete frontend/build/Maven, complete fresh jar/image Chromium, image runtime/security, final storage/maintenance verification, independent review and cleanup. Focused intermediate passes do not close these gates. |

Built-in browser observations and screenshots are retained in ignored `.cache/v2-batch-3/`; final counts/configurations and review findings will be added after verification.

## Pre-review whole-feature verification — 2026-11-03 (retained)

Verification targets the isolated `codex/v2-batch-3-acceptance` worktree under `.cache/v2-batch-3/acceptance-worktree`, based on `a810706` plus the original staged Batch 3 patch. The original checkout's subsequent readability cleanup is preserved separately. Its Java token/dependency equivalence and App extraction were audited; it is excluded from Batch 3 commits. The original checkout's current frontend211/build also passed as supplementary evidence. The October 30 full Maven run was interrupted before completion and is not claimed as a pass.

| Result | Actual command/configuration and evidence |
|---|---|
| PASS | Isolated `npm test -- --maxWorkers=1`: 211 tests / 12 files, 73.55s. `npm run build`: TypeScript/Vite, 32 modules; built before backend packaging. Logs `feature-frontend-test-20261103.log`, `feature-frontend-build-20261103.log` |
| PASS | Java 25 standard Maven Wrapper `verify`: 210 tests, zero failures/errors/skips, ArchUnit and real PostgreSQL 17.11 Testcontainers; current executable jar packaged, 10:57, finished 07:52:04 Asia/Taipei. Normal `target/test-classes` layout. Log `feature-maven-verify-20261103.log` |
| PASS | Complete packaged-jar Chromium: 28/28 workflows, one worker, zero retries, 3.6m. New `batch3_jar_20261103` database, loopback PostgreSQL55451 / application8099. Running jar is a byte-identical copy of the verified artifact; SHA-256 `89595B64392C8D8BF5D53B103823DC43F42083B01049BC6DB5688EF9F2B36B85`. Log `jar-browser-20261103.log` |
| FAIL — packaging environment, recovered | First image build failed before Java compilation: Windows `core.autocrlf=true` checked the Unix Maven shell wrapper out with CRLF, producing `./mvnw: not found`, exit127. Retained `image-build-20261103-crlf-failure.log`. Added the narrow `.gitattributes` rule `backend/mvnw text eol=lf` and normalized only that isolated wrapper. No dependency, Java/Windows wrapper or application behavior changed |
| PASS | Current pinned Docker build after the LF fix. Image `classroom-grouping:v2-batch3-20261103`, ID `sha256:9b324f834a578fe1ee18c4beffb64ae55d7ff8431aa0be5ea0a3265b7687bf9e`. Normal Docker packaging uses `-DskipTests`; the separate full 210-test run above is the integration evidence. Log `image-build-20261103.log` |
| PASS | Complete current-image Chromium: 28/28 workflows, one worker, zero retries, 4.2m. Separate new `batch3_image_20261103` database / loopback8100. Log `image-browser-20261103.log` |
| PASS | Image UID10001:10001, configured536870912 bytes (512MiB), health200, no OOM/restarts before/after; bounded samples299.3–347.8MiB. These are observations, not peak/hosted capacity proof. Session/CSRF/logout and scheduler separation: missing/invalid bearer401, browser session cannot authorize tick401, authenticated bounded tick204; browser login/logout without CSRF403. Log `image-security-20261103.log` |
| PASS | Both databases contain five actually finalized activities, five complete saved plans and zero missing finalized memberships. V2-202 has zero activities. Deleted title/body absent and latest deletion intent retained; no Assistant schema/vector extension. Three concurrent image ticks returned204 while the activity/group/participant/membership/plan/placement/work snapshot hash remained unchanged. Logs `jar-persisted-facts-20261103.log`, `image-persisted-facts-20261103.log`, `image-repeated-ticks-20261103.log` |
| PASS | Packaged `PropertiesLauncher` maintenance invocation on the explicit disposable jar database, with provisioning/scheduler flags enabled and application port already occupied: exit0, no Spring startup/listener, no obsolete backlog at this point. The PostgreSQL integration suite separately proves the 1,000-event bound, preservation and latest deletion retention. Log `packaged-maintenance-20261103.log` |
| PASS | Built-in browser development and current-jar interaction: teacher/student navigation, no-activity course, deep links, escaped text, keyboard cancellation/focus, max150-code-point unbroken title and max10,000-code-point multiline body. Current body DOM length10000 and zero interpreted script elements;390px document width375; sole active Announcements tab. Screenshots `current-jar-desktop-20261103.png`, `current-jar-mobile-20261103.png`; earlier developer observations in `frontend-progress.md` |
| PASS | Two existing invitation lost-response tests now await matching `requestfailed` before the unchanged alert assertion. This carries over only the documented November 2 synchronization correction, preserving its initial failed image run in the separate readability record. No assertion/timeout weakening or production change; both complete current artifact suites pass |
| PENDING at this historical pre-review checkpoint | Three fresh independent reviewers, final evidence/diff/preservation review and owned-service cleanup. The completed second-cycle and November5 finalization records below supersede this checkpoint |
| NOT RUN | Batch 3 remote CI, hosted acceptance, provider/AI work and owner manual acceptance. Local tests do not close hosted/operational gates |

Logs and screenshots named above are retained in the original workspace's ignored `.cache/v2-batch-3/`. Browser traces/network artifacts contain synthetic session headers and stay ignored; they are not publication artifacts. Fresh databases preserve all earlier test databases and project data. No push, PR, merge or deployment occurred.

## Change groups and proposed review boundary

One coherent PR is proposed: **Publish and manage course announcements with independent core acceptance**, based directly on merged Batch 2 `main`. It includes V11, Courses authorization/commands/receipts/catalog/outbox/maintenance and PostgreSQL tests; teacher/student navigation/UI/API and tests; connected A1/A2 plus retained V1/G1/G2 acceptance; portability/evidence updates. V2-3a and V2-3b share the receipt schema and recovery component, so splitting their final integrated state would manufacture a partial boundary. No downstream PR dependency is needed. The final findings and preservation/cleanup evidence are recorded below. The local commit hashes are reported in the final handoff; external action still requires approval.

The actual agent roster is `gpt-6.1-sol`, medium reasoning, `fork_turns=none` when spawned: two initial Researchers, three Implementers (backend, frontend, connected acceptance), one resume/isolation Researcher, and six fresh independent Reviewers across two cycles with distinct product/UX, authorization/privacy and transaction/storage lenses. Implementer follow-ups retain their workstream context and the same model/settings. The Lead runs as launched. No model substitution or independent-self-review claim is made. The frontend Implementer used the built-in browser during initial development; its resumed tool scope could not access IAB, so the Lead performed the correction's built-in interactions and shared observations/screenshots. The Implementer inspected those images and reported the limitation explicitly.

## Review remediation and final verification — 2026-11-03

Cycle 1 found one confirmed P2: publish from the list, then delete the inline detail, left `missing=true` and hid the Back link. A successful Refresh still showed unavailable. The Lead reproduced it in the packaged jar. The focused fix restricts unavailable-source state to explicit detail routes and clears it on successful list reads. Three unit regressions and a fourth real-backend announcement workflow prove list return, surviving notices, refresh and safe deleted direct links. Earlier automated passes are retained rather than presented as proof that this missing case was covered.

Security and transaction reviewers found no confirmed defects. Their stated coverage limits prompted three additional real PostgreSQL cases: an observed cross-course receipt uniqueness wait followed by assignment revocation; student detail/first/next-page reads revoked during observed course-lock waits; and a separate maintenance JVM racing delete/conditional acknowledgement in both queue orders with three observed PostgreSQL waiters. The current 17-method announcement suite passes. These were added proofs, not invented production defects; no Java production code changed during remediation.

| Result | Final corrected-tree evidence |
|---|---|
| PASS | `npm test -- --maxWorkers=1`: 214 tests / 12 files, 117.76s; focused announcement21 passed; `npm run build` passed (32 modules). Logs `feature-frontend-remediation-20261103-{focused,full,build}.log` |
| PASS | Full Java25 Maven Wrapper `verify`: 213 tests, zero failures/errors/skips, 5:40, finished08:20:52 Asia/Taipei; current assets packaged after the frontend build. Focused PostgreSQL17 announcement17 also passed. Logs `feature-maven-final-20261103.log`, `backend-remediation-20261103.log` |
| PASS | Complete final jar Chromium29/29, one worker, zero retries,3.1m; new `batch3_jar_20261103_r2` database. Final jar SHA256 `C3DEB23B15EFC0253BB7A3A30137C228FA2F06A6E0C069E5680918874BD85A73`. Log `jar-browser-final-20261103.log` |
| PASS | Final current pinned image built and complete image Chromium29/29, one worker, zero retries,3.9m; separate new `batch3_image_20261103_r2` database. Tag `classroom-grouping:v2-batch3-20261103-final`, ID `sha256:c827c9dfd4f1cd4627f14b46f6bf7a3143b6484f6b10aca6f82476516bdde10f`. Logs `image-build-final-20261103.log`, `image-browser-final-20261103.log` |
| PASS | Final image UID10001:10001/configured512MiB/health200/noOOM/restarts0; bounded303–353.1MiB observations. Session/CSRF/logout/scheduler separation rechecked. Log `image-security-final-20261103.log` |
| PASS | Both final databases: five finalized activities/five complete saved plans/zero missing finalized memberships; V2-202 zero activities; five announcements/three deleted, zero deleted rows with content or missing latest deletion intent, no Assistant schema/vector extension. Three concurrent authenticated ticks preserve the full core-state snapshot. Logs `jar-persisted-facts-final-20261103.log`, `image-persisted-facts-final-20261103.log`, `image-repeated-ticks-final-20261103.log` |
| PASS | Final packaged non-web maintenance invocation exits0 despite provisioning/scheduler flags and occupied web port; source/catalog/receipt/outbox snapshot unchanged. PostgreSQL tests separately prove backlog removal bound and both race orders. Log `packaged-maintenance-final-20261103.log` |
| PASS | Lead built-in browser reproduced the original trap, then verified corrected create/inline-delete/list/Refresh behavior against the real backend. Screenshot `inline-deletion-fixed-20261103.png`. Dedicated real-backend regression also passes; no mutation replay or deleted direct-link disclosure |
| PASS | Both independent review cycles are complete. All three fresh final reviewers reported no confirmed P1/P2/P3 findings on November3. Reviewers inspected source/evidence and did not rerun application suites. November5 finalization verifies preservation/content and cleanup below; no third cycle or new code change |

The new source remains on isolated `codex/v2-batch-3-acceptance`. Original staged Batch 3 patch and 170 other source/documentation/configuration files in the original checkout were checked unchanged during this run; `p.txt` is excluded from reads and Docker context. Final preservation/cleanup/commit details follow below. No user manual acceptance, remote CI, provider, hosted or deployment result is inferred.

## Final review, preservation and cleanup — 2026-11-05

The final product/UX, authorization/privacy and transaction/storage reviewers each completed their second-cycle report on November3 with **no confirmed findings**. They reviewed the corrected isolated diff and the recorded full-suite evidence; they did not author the implementation, rerun application suites or claim hosted acceptance. The first-cycle P2 is fixed and has three unit regressions plus the real-backend inline-deletion workflow. Finite synthetic workloads remain bounded evidence, not exhaustive concurrency, capacity or security proof.

The prior run stopped at an account usage limit after these review reports, before completing the final handoff. This resume required documentation and Git finalization, not another review cycle. Application/test source has not changed since those reports; both the retained running-jar copy and packaged artifact match SHA256 `C3DEB23B15EFC0253BB7A3A30137C228FA2F06A6E0C069E5680918874BD85A73`. No historical result is redated or called a November5 suite pass.

PASS: all 170 source/documentation/configuration files in the original checkout match the November3 preservation manifest. Its staged feature patch also retains SHA256 `C1AC4AC560565B3F3F99E8DAEC030EA35EAEE6A9F7831FAA60C5C052D525D1DE`. The unrelated readability/App-extraction work and V1 planning changes remain untouched there; `p.txt` is excluded from reads/staging and Docker context. The isolated V1 checkout remains clean at `c0a6dfc`; remote V1 and main heads remain `c0a6dfc` and `a810706` before this local commit. No V1 deployment/configuration/data change occurred.

PASS cleanup state: Docker29.7.2 was normally restarted only to inspect retained containers after its engine pipe was initially unavailable. Both acceptance app containers and the PostgreSQL container are stopped; no Batch3 host Java/Node/PostgreSQL process or listener remains on 8098/8099/8100/5185/55450/55451. Native `pg_ctl` reports no server running. PostgreSQL volume `4720493b6fa87c236d63ee86c97e96baaec4b2b5d498feaf3b1db9bd2f234893` remains. No container, volume or database was deleted. Evidence: ignored `cleanup-resume-20261105.log`, `preserved-files-resume-20261105.json` and original preservation/patch records.

**Retained shutdown limitation:** the final image was already stopped with exit137, OOMKilled=false and zero restarts; the previous image exited143 and PostgreSQL exited0. Final image logs show Tomcat graceful shutdown completed and JPA close began before termination. The cause of the forced final process termination is not established. This is not an application assertion failure or a normal-exit pass, and the container was not restarted merely to replace its recorded exit code. Earlier runtime/test no-OOM observations remain dated November3 evidence.

One coherent PR is proposed against `main`: **Publish and manage course announcements with independent core acceptance**. Prepare two local commits: the complete Courses/UI/DB/test/CI-portability implementation, followed by documentation and acceptance evidence. V2-3a/3b share the atomic receipt/outbox protocol and recovery UI, so this integrated result is not split into fragile intermediate PRs. All commits stay on `codex/v2-batch-3-acceptance`; the original checkout's staged/unstaged work is preserved separately. Batch3 remote CI and owner manual acceptance remain NOT RUN/pending. No push, PR, merge, deployment, AI/provider work or hosted validation is implied.

## Measured dormant storage

The isolated PostgreSQL 17.11 workload performed 1,000 edits to one source, then deleted it, and created 100 distinct sources with 50 deleted. It retained 101 source rows (50 active, 51 tombstones), 1,152 command receipts and 101 latest events (100 pending, one acknowledged), with **zero superseded events**. Acknowledgement/delete/compaction races and wrong-revision acknowledgement were checked separately from row counts. Exact command: Java 25 Maven Wrapper `-Dtest=AnnouncementIntegrationTest#dormantEditingStorageIsBoundedPerSourceAndConditionalAckCompactionRacesAreSafe test`; PASS, one test, no skips (16:09:25 Asia/Taipei). Log: `.cache/v2-batch-3/backend-announcement-storage-1000.log`.

| Relation | Scoped row bytes | Heap bytes | Index bytes | Total relation bytes |
|---|---:|---:|---:|---:|
| Sources/tombstones | 10,885 | 16,384 | 49,152 | 98,304 |
| Retained receipts | 194,688 | 212,992 | 106,496 | 344,064 |
| Metadata outbox | 9,701 | 40,960 | 278,528 | 344,064 |

Separate category queries measured active-source rows at 6,397 bytes, tombstones 4,485, pending events 9,597 and acknowledged events 101. `pg_column_size` measures tuple data; physical totals include indexes, auxiliary storage, dead tuples/free space and allocation granularity. This selected run contains only the stated announcement workload; the full-suite rerun has additional fixtures. Small synthetic bodies are not an estimate for maximum-length notices. Compaction bounds obsolete live edit-event rows per source; it does not make physical files or total storage constant.

Receipts averaged 169 tuple bytes here before indexes/physical overhead. One million retained commands would therefore need about 161 MiB for tuple data alone under this sample, plus indexes and other application data. That is a planning extrapolation, not hosted capacity proof. Distinct source/tombstone growth remains proportional to real usage; keys and tombstones have no automatic expiry. Larger active content, sessions, grouping history and allocation plans share the database budget.

Operator review thresholds for this implementation: investigate any sustained superseded backlog after writes (ordinary writes remove at most 64 obsolete events), run explicit bounded maintenance for accumulated backlog, and investigate latest pending count growth as distinct-source/deletion growth rather than silently deleting intents. Review measured `pg_total_relation_size` and total database usage when 50% of the owner-confirmed storage allowance is reached; at 75%, plan capacity/retention with the owner before exhausting headroom. These are operational review triggers, not product caps or validated hosted quotas. No account allowance was measured here. Vacuum/reuse and real content/command rates must be measured on the intended host; never drop retained keys/tombstones or assume downstream consumption to fit a quota.

## Remaining boundaries

Batch 3 remote CI is NOT RUN. Hosted cold-start, unattended scheduling, monitoring, recovery, combined quotas and deferred backup/resource tuning remain open. Local 204 scheduler responses do not prove finalization or hosted reliability. Assistant continuations of composite journeys are deferred to separately gated V2-5 onward; this batch proves only core G1/G2/A1/A2 behavior. No owner manual acceptance is inferred.
