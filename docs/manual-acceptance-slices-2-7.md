# Batch 1: manual acceptance guide for Slices 2–7

Prepared from the current repository on 2026-10-16. These are instructions for you to execute; preparing this guide did not start services, install dependencies, provision data, or run tests. Use synthetic data only.

The complete local application is PostgreSQL plus Spring Boot serving both the React website and APIs. A separate Vite server is unnecessary for this walkthrough.

- Website: **http://127.0.0.1:8080/**
- Optional health check: **http://127.0.0.1:8080/actuator/health** (expected HTTP 200 and `UP`).
- Database server: **127.0.0.1:5433**, PostgreSQL 17. Leave the unrelated PostgreSQL service on 5432 alone.
- Scope: profiles, activity creation/settings, group discovery/joining, applications, and invitations. After cutoff, **“Grouping is finalizing” is the expected endpoint of this batch**. Allocation, completed results, CSV export, administration, registration, password recovery, leaving/switching groups, and member removal are not implemented in this batch.

## 1. Start everything locally

Use PowerShell. Commands below assume the usual project database credentials. If you previously configured different database credentials, use those existing values consistently; changing an environment variable does not reset a persisted database password.

### A. Check prerequisites

1. Open Docker Desktop and wait for its Linux-container engine to be ready.
2. Open a PowerShell terminal, called **Application terminal** below.
3. Run:

```powershell
Set-Location 'C:\Users\wilso\OneDrive\桌面\Restart Plan\classroom-grouping'

node --version
npm --version
docker version
```

Expected: Node **24.x**, npm available, and Docker reports both Client and Server. A missing Server/pipe error means Docker is not ready; resolve that before continuing. Do not reset Docker or remove its volumes as a repair step.

Select Java 25 for this terminal. If this checkout still has its ignored downloaded JDK:

```powershell
$acceptanceJdk = Get-ChildItem .tools -Directory -Filter 'jdk-25*' | Select-Object -First 1
if (-not $acceptanceJdk) { throw 'Select an installed Java 25 JDK before continuing.' }
$env:JAVA_HOME = $acceptanceJdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Expected: Java **25**. Alternatively, set `JAVA_HOME` to your installed Java 25 directory and prepend its `bin` as above. Do not proceed with the machine's Java 17 default.

Check whether the app/port already has an owner:

```powershell
docker compose ps -a
Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
```

Expected: 8080 is available. If an old project app is running, stop it through its own terminal (Ctrl+C), or `docker compose --profile app stop app` if it is the Compose app. Do not kill an unidentified process. The existing project database can remain running.

### B. Start the project database and create a separate acceptance database

This uses the existing Compose PostgreSQL server but creates a **new named database**, preserving all existing databases, activities, credentials, and history. Keep its generated name if you want to resume this run later. Do not reuse databases from automated browser runs for a new complete journey: activities cannot be reset or replaced in V1.

```powershell
$env:DATABASE_PASSWORD = 'local-development-only'
$env:DATABASE_USERNAME = 'classroom_local'
$env:LOCAL_DATABASE_PORT = '5433'

docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) { throw 'Database did not become ready; stop here.' }

$acceptanceDatabase = 'manual_batch1_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
docker compose exec -T db createdb -U classroom_local -O classroom_local $acceptanceDatabase
if ($LASTEXITCODE -ne 0) { throw 'Database creation failed; stop and inspect the error.' }

$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:5433/$acceptanceDatabase"
Write-Output "Acceptance database: $acceptanceDatabase"
```

Expected: `db` becomes healthy and `createdb` exits successfully. The database name resembles `manual_batch1_20261016_153000`; your actual suffix will differ. This database starts empty. Flyway creates its application schema during application startup.

If `createdb` reports that the name already exists, choose another new name; do not drop the existing database. If your existing Compose database uses a custom password, substitute it above before starting the application. Compose's password setting only initializes a new volume; it does not change an existing database user's password.

### C. Install and build the real frontend

In the same Application terminal:

```powershell
Set-Location frontend
npm ci
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed; stop here.' }
npm run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed; stop here.' }
Set-Location ..
```

Expected: npm installation and TypeScript/Vite production build succeed. Dependency downloads require network access. This is a build, not an automated acceptance-test run.

### D. Enable only the batch fixture and start Spring Boot

This guide assigns a known password **only to newly created synthetic accounts in the new acceptance database**. It is an example local-test password, not a recovered password from existing databases.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:PORT = '8080'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:APP_DEMO_ENABLED = 'false'
$env:APP_ROSTER_ENABLED = 'false'
$env:APP_BATCH_FIXTURE_ENABLED = 'true'
$env:BATCH_FIXTURE_PASSWORD = 'LocalBatchOnly-2026!'

Set-Location backend
.\mvnw.cmd spring-boot:run
```

Expected: Maven downloads/builds as necessary, Flyway applies migrations, the batch fixture provisions successfully, and Spring Boot reports startup on port 8080. Leave this terminal open and running. Passwords are hashed in the database.

Open **http://127.0.0.1:8080/**. Expected: the real username/password sign-in page. Use this URL consistently; do not alternate between `localhost` and `127.0.0.1`. Do not use the mock prototype for these checks.

Fixture setup is additive and idempotent. It creates no activities and never overwrites existing passwords/history. If login fails against an older database, the example password may never have been that account's password. Use the original password or follow the new-database setup above; there is no password-reset UI.

## 2. Data and credentials for this run

### Database connection details

No database GUI or hand-written SQL is needed for the acceptance journey. If you want to inspect your synthetic database with a database client:

| Setting | Value |
|---|---|
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | The `manual_batch1_...` name printed in step B |
| Username | `classroom_local` |
| Password | `local-development-only`, unless you already use a custom value |
| JDBC URL | `jdbc:postgresql://127.0.0.1:5433/<your-acceptance-database>` |
| Original default database, preserved | `classroom_grouping` |

Database credentials are different from website account credentials. Do not edit tables to simulate acceptance outcomes; use the UI so permissions and rules are exercised. Account, course, activity, and group UUIDs are generated at provisioning/creation; they are not fixed IDs to copy from this guide.

### Website accounts

**All nine batch accounts use `LocalBatchOnly-2026!` in the new database created above.** Student usernames are different from Student IDs.

| Username | Role / displayed student name | Student ID |
|---|---|---|
| `batch-teacher` | Teacher | None |
| `batch-student-1` | 陳 Batch 一 | `00007001` |
| `batch-student-2` | Alex <Batch> & 二 | `00007002` |
| `batch-student-3` | Sam Batch 三 | `00007003` |
| `batch-student-4` | Riley Batch 四 | `00007004` |
| `batch-student-5` | Morgan Batch 五 | `00007005` |
| `batch-student-6` | Taylor Batch 六 | `00007006` |
| `batch-student-7` | Jordan Batch 七 | `00007007` |
| `batch-student-8` | Casey Batch 八 | `00007008` |

### Provisioned courses and the activity plan

`batch-teacher` is assigned to every course below. “Students 1–2” means `batch-student-1` and `batch-student-2`, etc. Introductions start empty; memberships and request histories start empty. **All activities below must be created manually through the teacher UI.** The labels in the Purpose column are guide labels, not additional activity-name fields.

| Course | Actual course name | Enrollments | Purpose / capacity list to enter |
|---|---|---|---|
| BATCH-101 | Batch Profile Classroom | Students 1–8 | Main profiles + creation: `2, 2, 2, 2` |
| BATCH-202 | Batch Separate Classroom | Students 1–2 | Course-specific profiles/access; leave without activity |
| BATCH-303 | Batch Empty Classroom | None | Creation must be disabled |
| BATCH-404 | Batch Settings Classroom | Students 1–2 | Settings: `2` |
| BATCH-405 | Batch Cutoff Classroom | Students 1–2 | Short-deadline settings: `2` |
| BATCH-505 | Batch Grouping Classroom | Students 1–8 | First joins + stale join conflict: `2, 2, 2, 2` |
| BATCH-506 | Batch Grouping Cutoff Classroom | Students 1–2 | Short-deadline joining: `2` |
| BATCH-606 | Batch Applications Classroom | Students 1–8 | Applications: `2, 2, 2, 2` |
| BATCH-607 | Batch Applications Cutoff Classroom | Students 1–4 | Short-deadline applications: `2, 2` |
| BATCH-707 | Batch Invitations Classroom | Students 1–8 | Invitations + combined cleanup: `3, 3, 2` |
| BATCH-708 | Batch Invitations Cutoff Classroom | Students 1–4 | Short-deadline invitations: `2, 2` |

For ordinary activities, enter deadline **15 March 2099, 14:30, America/New_York**. That deliberately distant date keeps long manual journeys open. For each short-deadline activity, choose **current New York time plus 10 minutes**, rounded up to a whole minute, immediately before creating it. If setup takes longer, extend it while still open. Once cutoff passes, an activity cannot be reopened.

Use these instructions for each activity, replacing the course code:

```text
BATCH-101 manual acceptance
Work together on a classroom project. 中文 😀
<b>This must remain plain text</b>
```

## 3. What each slice covers

| Slice | Features to accept manually |
|---|---|
| 2 | Names and exact Student IDs, search, profile dialog, own course-specific About me, empty/Unicode/plain text, 500-character limit, profile access after cutoff |
| 3 | Teacher-only setup/review/confirmation, valid future New York deadline, required instructions up to 5,000 characters, integer capacities ≥2 totaling all enrolled students, one activity per course |
| 4 | Saved instruction edits, later-only deadline extension while open, immutable capacities/participants, safe refresh and cutoff status, no teacher live grouping progress |
| 5 | Open group/member discovery, deliberate empty-group join, initial manager, conflicting joins, no automatic application, one membership per activity |
| 6 | Applications to occupied managed groups with space, private history, manager decisions, rejection/reapplication, no reserved places, joining/group-full cleanup |
| 7 | Manager invitations, recipient decisions, rejection/reinvitation, coexistence with applications, cleanup across both types, no overfill or double membership |

## 4. Use separate sessions and record outcomes

Use one browser profile/window for the teacher and a separate profile for students. For simultaneous actions, use distinct browser profiles or different browsers for each student. Normal tabs share a session; multiple private windows in the same browser may also share a session. They are not reliably separate actors.

To save resources, keep only the two or three actors needed for the current step open. When switching accounts within one session, **Sign out**, then sign in as the next account. Verify the displayed username before every action. In the checklist, **T** means `batch-teacher`; **S1–S8** mean the corresponding student accounts.

After another actor acts, click **Refresh groups**, **Refresh requests**, or **Refresh activity** in the observing session. Reopening the same tab/hash does not guarantee a new read. There are no push/email notifications.

Check each item only after its expected result appears. Record **PASS**, **FAIL**, or **NOT RUN**, with a screenshot or short note for failures. Prefer running sections A–G in order; section H contains supplementary checks.

### A. Arrive in class and introduce yourself — Slice 2

| Done | Action | Expected result |
|---|---|---|
| [ ] A1 | Sign in as T; inspect the course list. Sign in as S1 separately. | T sees all 11 batch courses. S1 sees their 10 enrolled courses, excluding BATCH-303. |
| [ ] A2 | S1 opens BATCH-101 → Classmates. | Eight student profiles appear, including S1; introductions are initially empty. No grouping assignments appear here. |
| [ ] A3 | Search for `00007002`, then open Alex's profile. | Exact ID `00007002` appears. Name `Alex <Batch> & 二` renders literally, without interpreting angle brackets as markup. |
| [ ] A4 | Close the dialog with Escape; open it again and use Close profile. | Both close the dialog and return focus to its trigger. Other students' profiles have no introduction-edit control. |
| [ ] A5 | S1 saves About me: `I enjoy design. 中文 😀 <b>hello</b>` using Save introduction. Reload. S2 opens S1's profile. | Success feedback, persisted text, literal `<b>hello</b>`, and leading-zero ID. S2 can read it but cannot edit S1's introduction. |
| [ ] A6 | S1 opens BATCH-202 → Classmates and saves a different introduction. Return to BATCH-101. | BATCH-202 contains only S1/S2. Its introduction is separate; BATCH-101 still has the earlier text. |
| [ ] A7 | In BATCH-202, save an empty introduction. Then paste 501 plain ASCII characters and attempt to save; reduce to exactly 500 and save. | Empty is allowed. 501 shows validation and cannot replace saved data; exactly 500 saves. The live counter helps check length. |
| [ ] A8 | S8 attempts the BATCH-202 course URL copied from S1's address bar. | Safe unavailable/no-access feedback; no course/profile data leaks. |

For A7, create a long text block in any text editor by repeating a short ASCII sentence and use the app's counter. There is no need to open a developer console.

### B. Teacher sets up the project — Slice 3

| Done | Action | Expected result |
|---|---|---|
| [ ] B1 | T opens BATCH-303. | Zero enrollments; Create activity is disabled with an explanation. |
| [ ] B2 | T opens BATCH-101 → Create activity. Before valid setup, try Review activity with empty/space-only instructions, missing/past deadline, capacity `1`, decimal `2.5`, and a total other than 8. Correct each field before trying the next case. | Clear validation; no activity is created. Capacities must be whole numbers ≥2 and total exactly eight. |
| [ ] B3 | Also try 5,001 ASCII instruction characters, then reduce to 5,000 and review valid input. Return to setup without confirming. | Oversized text is rejected; 5,000 is accepted for review. Review alone does not create the activity. |
| [ ] B4 | Enter the short sample instructions above, deadline 15 March 2099 14:30 New York, and four capacities of 2 using Add group. Select Review activity. | Review shows exact text, New York deadline, and ordered Groups 1–4. All eight enrolled students are included. |
| [ ] B5 | Select Back to setup, inspect preserved inputs, then review again. S1 refreshes Overview before T confirms. | Draft is preserved in this screen. S1 still sees no activity; there is no saved draft visible to students. |
| [ ] B6 | T clicks Confirm create activity once. S1 refreshes Overview. | “Activity created” feedback. Activity opens immediately; S1 sees the same instructions, deadline, and fixed capacities. Markup in instructions stays plain text. |
| [ ] B7 | T reloads the page and inspects available actions. S1 inspects their actions. | Only one activity exists. No second-create/reset/delete control; students have no teacher creation/settings controls. |

### C. Teacher clarifies instructions and extends time — Slice 4

Create BATCH-404 through the same setup/review/confirm flow, with capacity `2` and the ordinary 2099 deadline.

| Done | Action | Expected result |
|---|---|---|
| [ ] C1 | T chooses Edit instructions, saves `Revised plan. 中文 😀 <b>plain text</b>`. S1 refreshes Overview. | Instructions saved feedback; both see the new plain text. Deadline and capacity remain unchanged. |
| [ ] C2 | Attempt a blank or >5,000-character instruction edit; cancel editing afterward. | Validation rejects it; reloading still shows the last valid saved instructions. |
| [ ] C3 | T chooses Extend deadline. Try 15 March 2099 14:30, then 14:29. | Equal/earlier deadline rejected. Saved deadline remains 14:30. |
| [ ] C4 | Extend to 15 March 2099 14:31. Refresh as S1. | Deadline extended feedback; both see 14:31 labeled America/New_York. Instructions/capacity remain unchanged. |
| [ ] C5 | Inspect T's view after students begin joining in later sections. | T sees activity settings, not current members, request histories, occupied-group totals, or live grouping progress. No participant/capacity editing controls. |

### D. Students discover groups and choose managers — Slice 5

Create BATCH-505 with capacities `2, 2, 2, 2` and the ordinary deadline. Keep S1 and S2 in separate sessions.

| Done | Action | Expected result |
|---|---|---|
| [ ] D1 | Both students open Groups and inspect Group 1 before either joins. | Four empty groups, each two available places; both students are ungrouped. |
| [ ] D2 | S1 clicks Join Group 1, reads the warning, then Cancel. | No membership or manager is created. |
| [ ] D3 | Without refreshing S2, S1 opens Join Group 1 again and Confirm join. | S1 becomes Group 1's sole member and manager; one place remains. |
| [ ] D4 | On S2's stale empty-group screen, click Join Group 1 → Confirm join. | Conflict such as “This group is no longer empty”; refreshed state shows S1. S2 remains ungrouped and has no automatically submitted application. If the screen already refreshed, record the stale-conflict case NOT RUN rather than inventing a pass. |
| [ ] D5 | S2 deliberately joins empty Group 2. | S2 becomes its initial manager/member; Group 1 is unaffected. |
| [ ] D6 | S3 opens Groups, inspects S1's member name, ID, About me and Manager label. | Correct permitted profile details and availability; occupied groups offer Apply, empty groups offer Join. |
| [ ] D7 | Inspect S1's controls and reload their page. | Membership persists; no join-another-group, leave, switch, transfer-manager, rename, or remove-member action. |

### E. Students apply and managers decide — Slice 6

Create BATCH-606 with capacities `2, 2, 2, 2` and the ordinary deadline. **Memberships are course/activity-specific:** S1 joins Group 1 here and S2 joins Group 2 here, regardless of their BATCH-505 memberships.

| Done | Action | Expected result |
|---|---|---|
| [ ] E1 | S3 selects Apply to Group 1; Cancel the dialog. Inspect Requests → Applications. | No attempt was recorded. |
| [ ] E2 | S3 applies again and Confirm application. Refresh groups and requests. | One PENDING attempt; S3 remains ungrouped and Group 1 still has one available place. No duplicate pending application or Withdraw control. |
| [ ] E3 | S1 opens Requests → Applications, refreshes, and locates Sam / `00007003`. S2 and S5 inspect their own application histories. | S1 and S3 see this attempt. S2/S5 do not see it. Only S1 has Approve/Reject for this request. |
| [ ] E4 | S1 rejects the application and confirms. S3 refreshes, then applies to Group 1 again. | First attempt stays REJECTED; a new PENDING attempt appears. Rejection does not permanently ban reapplication. |
| [ ] E5 | S3 also applies to Group 2. S4 applies to Group 1. | These pending requests coexist. Group 1 still has one available place; no places are reserved. |
| [ ] E6 | S1 opens approval for S3, then Cancel. Refresh. | S3's application remains pending and S3 remains ungrouped. |
| [ ] E7 | S1 approves **S3** and Confirm approval. All actors refresh their views. | S3 joins Group 1 as an ordinary member; S1 stays manager; group is full (2/2). Accepted attempt is ACCEPTED. S3's Group 2 application is canceled with “Joined a group”. S4's Group 1 application is canceled with “Group full”. The earlier rejected attempt remains REJECTED. |
| [ ] E8 | S3 inspects Requests; S4 inspects Groups; T attempts student-only routes as described below. | S3 cannot approve/invite as an ordinary member. S4 cannot newly apply to full Group 1, but remains eligible elsewhere. T cannot read or decide student requests. |

### F. Managers invite; requests coexist and close together — Slice 7

Create BATCH-707 with capacities **`3, 3, 2`** and the ordinary deadline. S1 joins Group 1; S2 joins Group 2 in this course. Start the following sequence with S3–S5 ungrouped here.

| Done | Action | Expected result |
|---|---|---|
| [ ] F1 | S3 applies to Group 1. S1 opens Requests → Invitations → Invite Sam Batch 三, then Cancel. | Application remains pending; no invitation recorded by merely opening/canceling the dialog. |
| [ ] F2 | S1 repeats Invite Sam → Confirm invitation. S3 refreshes both Applications and Invitations. | One PENDING application plus one PENDING invitation to the same group. S3 is still ungrouped; Group 1 still has two available places. |
| [ ] F3 | S3 selects Reject invitation → Confirm rejection. | Invitation becomes REJECTED. The application remains PENDING. |
| [ ] F4 | S1 refreshes Invitations and sends a new invitation to S3. Then S1 rejects S3's application in Applications. | New invitation PENDING alongside retained REJECTED invitation. Application becomes REJECTED; invitation remains PENDING. |
| [ ] F5 | S3 reapplies to Group 1 and also applies to Group 2. S2 invites S3 to Group 2. | Both application types and invitations coexist across groups; none reserves places. |
| [ ] F6 | S1 invites S4 and S5 to Group 1. S4 and S5 each also apply to Group 1. | Three recipients can have pending invitations even though only two places remain. The one-pending-per-pair rule holds separately for each request type. |
| [ ] F7 | S3 accepts the **Group 1** invitation and confirms. Refresh all relevant histories/groups. | S3 joins Group 1; one place remains. Accepted invitation stays ACCEPTED. S3's other pending applications (including Group 1 counterpart and Group 2) and Group 2 invitation are canceled with “Joined a group”. Earlier rejected attempts remain unchanged. S4/S5 requests remain pending. |
| [ ] F8 | S4 accepts the Group 1 invitation and confirms. Refresh S4/S5/S1. | Group 1 is full with S1/S3/S4. S4 invitation ACCEPTED; S4 counterpart application canceled with “Joined a group”. S5 pending invitation AND application canceled with “Group full”; S5 remains ungrouped. |
| [ ] F9 | Inspect S3/S4 controls and S6's private history. | Ordinary members cannot invite or decide others' applications. S6 cannot see S1's private requests. Full-group invitation controls are unavailable. |
| [ ] F10 | Reload S1/S3 histories and inspect previous outcomes. | Attempts and reasons persist. No Withdraw, Cancel invitation, switch-group, or respond-to-completed-request controls. If Load older appears, it reveals retained older attempts. |

### G. Reach a real deadline — Slices 2, 4–7

Do this last. Create the four cutoff activities **one at a time**, with a fresh deadline about 10 minutes ahead for each. Use actual New York time, not the distant 2099 date. Do not change your computer clock or database rows.

| Done | Action | Expected result |
|---|---|---|
| [ ] G1 | T creates BATCH-405 with capacity `2`. While it is open, verify Extend deadline exists. Wait until the entered deadline, then Refresh activity. | Closed/finalizing message; deadline extension disappears. It cannot be reopened. No final roster appears. |
| [ ] G2 | T edits and saves BATCH-405 instructions after cutoff. S1 refreshes Overview, reads a classmate profile, and saves their own About me. | Instruction edit and course profiles still work. Deadline stays unchanged and grouping stays closed. |
| [ ] G3 | T creates BATCH-506 with capacity `2`. S1 opens Groups while it is still empty; leave Join Group 1 confirmation open across the deadline, then confirm. | Server rejects the stale join; no membership is created. Refresh shows finalizing, with no current group roster or joining controls. |
| [ ] G4 | T creates BATCH-607 with capacities `2, 2`. Before cutoff, S1 joins Group 1 and S3 applies to it. Leave the application pending. After cutoff, refresh Requests as S1/S3. | Pending application displays EXPIRED. Neither Approve nor Reject is available. Earlier completed outcomes, if any, stay completed. |
| [ ] G5 | T creates BATCH-708 with capacities `2, 2`. Before cutoff, S1 joins Group 1 and invites S3. Leave it pending. After cutoff, refresh Invitations as S1/S3. | Pending invitation displays EXPIRED. Accept invitation and Reject invitation are unavailable. |
| [ ] G6 | After G4/G5, visit Groups, Requests, Overview and Classmates; repeatedly use read-only refresh/reload. | Groups/results remain finalizing without partial member lists. Private history remains readable; profiles still work. Refresh creates no new memberships/attempts and does not complete allocation. |

If you miss a pre-cutoff setup action, record the case NOT RUN. Do not reset/delete the activity to retry. For a fresh complete journey, create another new acceptance database through step B and seed it on startup.

### H. Supplementary access, race, usability, and failure checks

These checks add useful coverage; manual timing does not prove all database concurrency guarantees.

| Done | Action | Expected result |
|---|---|---|
| [ ] H1 | Copy a course URL, then append `/groups`, `/requests`, or `/classmates` after its course UUID in the hash. Open each as T, e.g. `http://127.0.0.1:8080/#/courses/<actual-UUID>/requests`. | Teacher receives unavailable/no-access feedback, not live rosters or private requests. Use a real UUID copied from the address bar, not the placeholder. |
| [ ] H2 | In a spare two-place group in BATCH-606, have S6 join first, then S7/S8 apply. Open each approval dialog in two separate sessions both logged in as S6. Confirm both close together. | Exactly one applicant joins the last place; the other remains ungrouped and their request ends “Group full”. Conflict/stale-state feedback is acceptable; refresh all views. Never 3 members in a capacity-2 group. |
| [ ] H3 | In BATCH-707 Group 3 (capacity 2), S6 joins as manager, S7 applies, and S6 invites S8. Open S6's approval and S8's acceptance before either confirms; confirm close together. | Exactly one joining request succeeds; the other ends “Group full” after refresh. Group never exceeds two members; no student acquires two memberships. |
| [ ] H4 | In BATCH-101, S6 and S7 join different empty groups as managers. Both invite ungrouped S8. Open Group 1/2's applicable invitation acceptance dialogs in two separate S8 sessions and confirm close together. Use the actual groups S6/S7 joined. | S8 joins exactly one group; the other invitation is canceled with “Joined a group”. Refresh determines the winner; no particular winner is required. |
| [ ] H5 | Narrow the browser to about 390 pixels wide or use browser device emulation. Navigate forms, profiles and dialogs with Tab/Shift+Tab, Enter, Escape. | Text/IDs/actions readable without page-wide horizontal scrolling; dialogs usable, Escape cancels, focus returns sensibly. Cancel does not submit. |
| [ ] H6 | Sign out while on a course deep link. Revisit it and sign in again. Optionally leave a session idle for more than 30 minutes before a read. | Authentication required; authorized deep link is restored. Expiry prompts sign-in without silently resubmitting a mutation. |
| [ ] H7 | Optional: in browser DevTools → Network, select Offline, attempt an About me save, then return Online. Use Check saved introduction before another explicit save. | Failure/uncertainty is explained; saved data can be checked and the draft reviewed. There is no automatic save replay. Return Network to Online afterward. |

H7 checks recovery controls for a disconnected client. Offline mode alone **does not prove** the harder case where the server commits but the response is lost. That needs controlled transport interception; this repository's real-backend browser tests cover it. Similarly, a manual race that happens sequentially should be recorded as such, not claimed as proof of concurrent transactions. Direct API authorization, CSRF, rollback, and database constraints require the existing automated integration tests for complete evidence.

If any action says it “could not be confirmed”, it may already have succeeded. Use the offered **Check activity**, **Check current groups**, **Refresh requests**, or **Check saved introduction** read first. Review current saved state before making another deliberate action; do not repeatedly click a mutation to discover its outcome.

## 5. Safely stop and resume

1. Return DevTools Network to **Online** if used. Sign out of each synthetic browser session.
2. In the Application terminal running Maven, press **Ctrl+C once**. Wait for Spring Boot/Maven to finish shutdown and return to the prompt.
3. If you also started Vite separately, press Ctrl+C in that terminal. This guide does not require Vite.
4. In a PowerShell terminal at the repository root, stop the project database **only if no other local project process needs it**:

```powershell
Set-Location 'C:\Users\wilso\OneDrive\桌面\Restart Plan\classroom-grouping'
docker compose stop db
docker compose ps -a
```

Expected: the `db` container is stopped and its persistent volume remains. If you knowingly started the optional Compose app too, stop it with `docker compose --profile app stop app` before stopping `db`.

5. Record your acceptance database name, account password, outcomes and screenshots. Keep credentials out of committed files. Closing the Application terminal clears its temporary environment variables. Do **not** use `docker compose down -v`, volume removal, `dropdb`, Flyway clean, or manual table deletion for routine shutdown.

To resume **the same run**, start `db` again; repeat Java/profile/database environment setup using the **recorded existing acceptance database name** instead of creating another database. Set the fixture flag to `false` because accounts already exist:

```powershell
# In a fresh Application terminal, first select Java 25 as in section 1A.
Set-Location 'C:\Users\wilso\OneDrive\桌面\Restart Plan\classroom-grouping'
$env:DATABASE_PASSWORD = 'local-development-only'  # Or your existing custom value
$env:DATABASE_USERNAME = 'classroom_local'
$env:LOCAL_DATABASE_PORT = '5433'
docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) { throw 'Database is not ready.' }
$acceptanceDatabase = Read-Host 'Paste the existing manual_batch1 database name'
$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:5433/$acceptanceDatabase"
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:PORT = '8080'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:APP_DEMO_ENABLED = 'false'
$env:APP_ROSTER_ENABLED = 'false'
$env:APP_BATCH_FIXTURE_ENABLED = 'false'
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Existing accounts/passwords/history remain; expired activities remain closed. There is no need to rebuild unchanged frontend assets. To repeat all creation/mutation journeys from scratch, make a **new named acceptance database** and enable the batch fixture for its first startup; preserve the previous run.

## 6. Record the result accurately

| Area | Your result | Evidence / issue |
|---|---|---|
| Local startup and fixture setup | NOT RUN | Fill in after executing |
| Slice 2: profiles | NOT RUN | A1–A8, G2 |
| Slice 3: creation | NOT RUN | B1–B7 |
| Slice 4: settings/status | NOT RUN | C1–C5, G1–G2 |
| Slice 5: discovery/joining | NOT RUN | D1–D7, G3 |
| Slice 6: applications | NOT RUN | E1–E8, G4 |
| Slice 7: invitations/cleanup | NOT RUN | F1–F10, G5 |
| Supplementary checks | NOT RUN | G6, H1–H7; distinguish timed races from actual concurrency evidence |
| Safe shutdown | NOT RUN | Retained database name; stopped owned services |

For a failure, note the course, actor, step, expected/actual result, New York time, and screenshot. Startup problems such as Docker unavailable, missing Java 25, port occupied, dependency-download failures, or wrong existing DB credentials are environment blockers; distinguish them from application behavior failures.

Prior recorded batch checks passed locally on 2026-10-15; they are not a new pass for your manual run. Remote private GitHub CI/no-cost allowance, hosted cold starts, unattended scheduling, monitoring, recovery/quotas, backups, and final resource tuning remain open/deferred. Earlier Docker socket failures are historical in the latest repository evidence; this guide has not rechecked today's engine. The ordinary authenticated scheduler intentionally returns 501; it does not allocate students.

Sources: [README](../README.md), [roadmap](implementation-roadmap.md), [PRD](PRD.md), [technical decisions](tech-stack.md), [batch evidence](slices-2-7.md), and the current frontend, batch fixture and real-backend browser-test source.
