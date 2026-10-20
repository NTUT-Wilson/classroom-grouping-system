# Project-wide timezone amendment

Authorized on 2026-10-19 during V2 discovery: replace the university timezone with America/New_York across V1, V2, and future versions, including relevant implementation and tests. Other V2 features remain in requirements discovery.

## Changes

Deadline creation and extension resolve New York wall time independently of the device timezone, with seasonal offsets checked against IANA data. Both nonexistent and ambiguous local times are rejected. The extension editor converts saved instants using the university timezone. Deadline displays identify the university timezone and seasonal zone abbreviation; application/invitation timestamps and the mock prototype use the university timezone too.

The backend already accepts offset-bearing timestamps, persists instants, and validates against database time. Its instant-based contract is retained; no database migration, existing-deadline rewrite, or data reset is needed. Wall-time ambiguity rejection applies to wall-time entry; a timestamp with an explicit offset already identifies an instant.

Current requirements, architecture, flows, roadmap, prototype guide, and manual acceptance instructions use New York. Historical slice reports retain their actual past timezone and verification evidence; they do not establish current amendment verification.

Unit coverage includes winter and summer offsets, invalid dates, spring gap/fall overlap rejection, times on both sides of each transition, saved-instant conversion, and invalid extension submission without a request. Real-backend browser expectations use New York labels and matching saved UTC instants; those workflows still require execution.

## Verification — 2026-10-19

- PASS: Final frontend single-worker test run: 180 tests across ten files, followed by the final TypeScript/Vite build after all implementation changes.
- PASS: Content/diff review (`git diff --check`); only line-ending normalization warnings.
- PASS: Complete prototype Chromium suite, seven tests, one worker, zero retries (14.9 seconds), outside the sandbox. Initial sandbox attempts stalled before test execution because loopback socket access was denied and were interrupted; the successful retry resolved that browser environment restriction. Playwright managed the temporary dev server lifecycle.
- FAIL — environment: Java 25 Maven Wrapper verify attempted; 29 tests reported, zero assertion failures, seventeen Docker/Testcontainers setup errors. Docker Linux-engine named pipe is unavailable, confirmed both inside and outside the sandbox. This is not a full integration pass.
- NOT RUN: real-backend browser workflows and revised container validation, blocked by unavailable Docker engine. No persistent database reset, substitute native-database verification, or deployment.
- Remote CI allowance, hosted validation, unattended scheduling, monitoring, recovery, quotas, and deferred backup/tuning gates remain open.
