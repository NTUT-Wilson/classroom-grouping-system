# Approved V1 implementation roadmap

Approved by the user on 2026-10-13. This roadmap continues from the existing Project Foundation and approved UI prototype; it does not authorize starting Slice 1, deployment, cloud provisioning, or paid execution.

The [PRD](PRD.md) governs product behavior, the [technical stack](tech-stack.md) governs technical decisions, and [AGENTS.md](../AGENTS.md) governs repository practices. Consult the [user flows](user-flows.md), [glossary](../GLOSSARY.md), and [UI prototype guide](ui-prototype.md). Preserve the approved English, course-centered UI and all permissions and lifecycle restrictions.

## Progress tracker

Allowed statuses: **Not Started**, **In Progress**, **Completed**. Mark a slice Completed only when its deliverables and acceptance criteria have supporting verification evidence; record unresolved checks separately. Existing Foundation and mock prototype work do not count as completed business slices.

| Slice | Status | Verification evidence / remaining work |
|---|---|---|
| 1. Accounts and course workspace | Completed | Local acceptance 2026-10-13: frontend 25 tests/build; full Maven 27 PostgreSQL/ArchUnit tests; jar and image Chromium 8 each; prototype 7; two fresh independent reviewers. See [Slice 1 evidence](slice-1.md). Remote CI/hosting remain open; preserved baseline must be settled before publishing. |
| 2. Classmate profiles | Not Started | Implementation not started. |
| 3. Activity creation | Not Started | Implementation not started. |
| 4. Instructions, deadline, and status | Not Started | Implementation not started. |
| 5. Group discovery and first joining | Not Started | Implementation not started. |
| 6. Applications | Not Started | Implementation not started. |
| 7. Invitations and combined request behavior | Not Started | Implementation not started. |
| 8. Deadline processing and saved allocation plan | Not Started | Implementation not started. |
| 9. Allocation completion and recovery | Not Started | Implementation not started. |
| 10. Final result screens | Not Started | Implementation not started. |
| 11. Final CSV export | Not Started | Implementation not started. |
| 12. Complete V1 acceptance | Not Started | Implementation not started. |

## Implementation order and acceptance

Use the following sequential order. Dependencies identify prerequisites within the roadmap. Each slice includes necessary forward Flyway migrations, backend permissions and rules, frontend integration, and meaningful tests. Mock fixtures remain separate from real APIs.

| Slice | Depends on | Deliverable and acceptance criteria |
|---|---|---|
| **1. Accounts and course workspace** | Existing Foundation | Extend provisioned accounts with student identity, courses, assignments, and enrollments. Real login opens only permitted courses; direct access to another course is denied. Setup is repeatable without overwriting existing data. |
| **2. Classmate profiles** | 1 | Connect Classmates and About me to stored data. Preserve Student IDs exactly, including leading zeros. Students edit only their own course-specific introduction, up to 500 characters; profiles remain readable after cutoff without exposing final assignments. |
| **3. Activity creation** | 1 | Connect teacher setup and confirmation. Validate instructions, future Taipei deadline, and manual capacities against all enrolled students. Creation atomically fixes participants and groups and opens participation. Concurrent creation produces only one activity per course. |
| **4. Instructions, deadline, and status** | 3 | Connect settings and lifecycle views. Instructions remain editable after cutoff; deadline extensions work only while open and only move later. Participants and capacities stay fixed. Teachers receive no live grouping progress. |
| **5. Group discovery and first joining** | 2, 4 | Connect student group browsing and deliberate empty-group joining. First join establishes the manager. Competing joins cannot create duplicate membership or overfill groups; the losing student receives a conflict without an automatic application. |
| **6. Applications** | 5 | Implement submission, manager approval/rejection, and private history. Applications require an occupied group with space. One pending application per student/group; rejection permits a new attempt and preserves history. Approval joins atomically and closes affected pending applications with the correct reasons. |
| **7. Invitations and combined request behavior** | 6 | Implement manager invitations and recipient responses. Applications and invitations may coexist without reserving places. Rejection affects only its request; successful joining closes all affected pending requests. Test competing approvals, acceptances, and last-place joins across both request types. |
| **8. Deadline processing and saved allocation plan** | 4, 7 | Replace the scheduler placeholder with durable due-work processing. Expire pending requests and save one complete allocation plan before assignment. Startup catch-up and repeated triggers reuse the same plan. Closed screens offer read-only refresh without partial results. |
| **9. Allocation completion and recovery** | 8 | Apply the saved plan and finalize only when every fixed participant has exactly one valid membership. Preserve existing memberships and managers; system-only groups have no manager. Restart and interrupted-processing tests prove recovery without reshuffling or duplicate assignment. |
| **10. Final result screens** | 2, 9 | Connect approved result layouts. Teachers see the complete finalized roster; students see only their own group. Direct API access, request history, and classmate views cannot reveal another student's final assignment. |
| **11. Final CSV export** | 10 | Deliver teacher-only export after complete finalization. Verify the exact three columns, ordering, Unicode, quoting, leading-zero IDs, and every participant appearing once. Unsafe spreadsheet values reject the entire export; failures provide retry feedback. |
| **12. Complete V1 acceptance** | 1–11 | Run complete teacher/student journeys against the real backend, including denied access, cutoff races, uncertain responses, recovery, and export. Verify approved desktop/mobile layouts and keyboard use. Preview controls and synthetic data must not influence real workflows. |

Transaction safety, database constraints, duplicate-command protection, and authoritative deadline checks belong in the first relevant write slice, rather than being deferred to final testing. Follow the approved locking, idempotency, recovery, and privacy decisions in the technical stack. Never automatically replay a mutation after an uncertain response.

Every slice must pass its relevant frontend, backend, real PostgreSQL, and real-backend browser checks before being called verified. Add concurrency, authorization, and failure tests alongside the behavior they protect; Slice 12 expands full-system acceptance rather than postponing these checks. Record PASS, FAIL, or NOT RUN with evidence and distinguish application failures from environment blockers.

## Outstanding validation tracks

These tracks run alongside local feature work. They remain open and are not satisfied by roadmap approval, mock UI tests, historical passes, or selected native database checks. The entries below reflect the last recorded evidence, not a fresh environment check.

| Track | Current recorded status | Required follow-up |
|---|---|---|
| **Foundation: full integration** | PASS locally 2026-10-13: current full Maven verification, 27 tests including PostgreSQL Testcontainers and recovery; no skips. | Preserve current evidence; hosted feasibility remains separate. The earlier socket failure is historical. |
| **Foundation: image/runtime** | PASS locally 2026-10-13: revised image UID 10001, 512 MiB limit, health/security checks and eight Chromium tests. | Hosted workloads and final resource tuning remain separate; local memory samples do not establish hosting suitability. |
| **Private GitHub CI** | NOT RUN remotely for the revised workflow. | Confirm no-cost runner allowances and spending controls, then obtain actual remote test/build/runtime results. |
| **Hosting feasibility** | NOT RUN: hosted access and validation remain outstanding. | Validate cold starts, unattended external scheduling, missed-run detection, retry/timeout behavior, database/session recovery, and combined compute, storage, and network quotas. Local success is not hosted evidence. |
| **Operations and recovery** | Deferred/open. | Resolve scheduler arrangements, backups, restore evidence, and final resource tuning. Obtain separate approval for choices affecting recurring cost, security/privacy, product requirements, or major architecture. |

Detailed evidence and follow-up requirements remain in [foundation.md](foundation.md) and [hosting-validation.md](hosting-validation.md). Recheck them before changing a gate's status. Local development may proceed as chosen by the user, but Foundation/hosting must not be described as fully validated while these gates remain open. This roadmap authorizes no deployment, paid provisioning, or change to approved product requirements.
