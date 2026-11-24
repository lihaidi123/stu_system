# Next Stage Handoff: Authentication and Full Product Acceptance

Updated: 2026-11-23

## Purpose

Continue from accepted Stage 4 toward the full requirements in `docs/开发文档.md`. Stage 4 is complete for its clarified desktop-only scope. The product as a whole is not complete: CAPTCHA lifecycle, full role-specific workflows, remaining administration requirements, and QA-22's complete keyboard/viewport review still need implementation or evidence.

This document is a continuation handoff and stage proposal. It does not change the requirements baseline or claim the open IDs are complete.

## Verified Baseline

- Stage 3 grade consistency is complete for its defined scope; QA-15 user/account import subcases were deferred to file work and now have Stage 4 account import coverage.
- Stage 4 account and grade workbooks, profile/contact/username/password, avatar lifecycle, server scope, failure audit/redaction, Redis outage fail-closed behavior, and revoked-token rejection after a full OS-process restart passed isolated acceptance on 2026-11-23.
- Final Stage 4 suite: 92 total tests, 0 failures, 0 errors, 22 skipped. All 24 Stage 4 MySQL tests ran; 21 Stage 3 MySQL tests skipped because their separate schema was not configured. The opt-in Redis service-disconnect test ran separately and passed.
- Real-API ADMIN browser flows and screenshots at 1440x900 and 1280x800 are recorded in `docs/evidence/stage4/README.md` and `docs/evidence/stage4/ego/`.
- Java package and Vue production build passed. The authenticated workbench is lazy-loaded; final emitted chunks were 284.21 kB login JS, 264.34 kB workbench JS, and 371.47 kB CSS, with no Vite chunk warning.
- The disposable Stage 4 schema and temporary API/Redis/Vite processes were removed. Shared MySQL, shared Redis, and `stu_system` were not used for destructive tests.

## Open Requirement Groups

| Priority | Requirements | Current gap |
|---|---|---|
| 1 | AUTH-02, QA-02 | Login has no one-time CAPTCHA endpoint or challenge-consumption flow. The specification calls for a server-issued challenge with a default 120-second lifetime, one-time consumption, expiry/reuse rejection, and bounded issuance. |
| 2 | AUTH-01..06, QA-01..06, QA-12, QA-24 | Complete authenticated workflows and data-scope parity for ADMIN, TEACHER, COUNSELOR, and STUDENT. Existing UI menus cover some role routes, but counselor grade/detail workflows and broader role-specific states require a route-by-route audit. Server authorization remains authoritative. |
| 3 | ADM-01..15, PRO-01..07, SYS-01..02, AOP-01..04, QA-15..21, QA-23..24 | The matrix still groups unfinished administration, identity, error-state, audit, and data-scope requirements. Resolve each ID against current source and executable evidence before changing its status. |
| 4 | QA-22 | Real Stage 4 desktop review is complete at 1440x900 and 1280x800. The broader four-role review at 1440x900, 1280x800, 768x1024, and 390x844, including keyboard operation and failure/expired states, remains open. |

## Proposed Work Stages

### Stage 5A: One-Time CAPTCHA and Login Lifecycle

Implement AUTH-02 and close the CAPTCHA portion of QA-02 before expanding login UI behavior.

Inspect and update, as needed:

- Backend: `src/main/java/edu/stusystem/api/AuthController.java`, `src/main/java/edu/stusystem/service/IdentityService.java`, `src/main/java/edu/stusystem/service/AtomicRateLimitService.java`, `src/main/java/edu/stusystem/security/SecurityConfig.java`.
- Frontend: `frontend/src/App.vue` and the authenticated entry flow in `frontend/src/DashboardView.vue` only where the login contract requires it.
- Contracts and tests: `docs/API.md`, `docs/DATA_MODEL.md` if persistence changes, `docs/REQUIREMENTS.md`, `docs/TESTING.md`, focused unit/Redis/MySQL tests.

Required behavior:

- Issue a random challenge ID and image through the documented public endpoint; keep the expected answer server-side as a digest, not in the client response, source, browser storage, or logs.
- Enforce the documented 120-second default lifetime and atomically consume a challenge on verification. A challenge must not be reusable after a correct or incorrect attempt.
- Validate the challenge as part of login before password authentication. Return stable errors for missing, wrong, expired, and reused challenges without revealing account existence or credentials.
- Bound challenge issuance and preserve the existing login rate limit. Redis failure must fail closed with a stable service-unavailable response; it must never disable throttling or restore revoked credentials.
- Do not add a durable schema migration for ephemeral challenge state unless the implementation evidence requires one; Redis is the documented short-lived state store.

Acceptance evidence:

- Verify correct, wrong, missing, expired, reused, and concurrent same-challenge submissions.
- Verify login lockout remains account/alias scoped and CAPTCHA failures cannot bypass or reset the login limit.
- Verify Redis outage fails closed and no answer, password, token, or challenge secret appears in logs/responses.
- Exercise the login page against an isolated API with a synthetic account and record browser evidence. Never store the secret answer in an automation fixture or tracked file.

### Stage 5B: Four-Role Workflow and Requirement Parity

Build from the route-by-role matrix in sections 8 and 10 of `docs/开发文档.md`, not from visible navigation alone.

- ADMIN: organization, terms, students, accounts/grants, grades, statistics, and audit workflows required by ADM-01..15.
- TEACHER: only assigned class/course intersections for options, student records, grade details, create/update/delete, imports, exports, and summaries.
- COUNSELOR: assigned-class records, read-only grade/detail and class summary workflows; no grade mutation or cross-class discovery.
- STUDENT: own profile/grades/summaries only; never trust a client-supplied student ID.
- For every workflow, cover loading, empty, error, retry, permission-denied, validation, and expired-login states with accessible controls.
- Add or complete isolated MockMvc/MySQL tests for each role and each resource class before declaring a UI route accepted. The client must not implement scope as the security boundary.

### Stage 5C: QA-22 and Final Requirement Closure

- Review actual local pages for the four roles at 1440x900, 1280x800, 768x1024, and 390x844 using ego-browser.
- Exercise keyboard Tab/Enter/Escape, modal focus, table overflow, long names, empty/loading/failure/permission/expired states, and visible focus. Keep page-wide horizontal overflow out of narrow viewports.
- Search LeiBox for only the components relevant to a demonstrated workflow need and update `docs/COMPONENT_SOURCES.md` with source/license/dependency findings; do not add a component for catalog coverage alone.
- Update `docs/REQUIREMENTS.md` ID by ID from executed test and browser evidence; do not mark grouped rows complete because one role or viewport passed.
- Run the final Maven suite, frontend build, OpenAPI smoke, isolated database integration, and the applicable browser pass. Keep Stage 4's desktop-only clarification distinct from QA-22's broader four-viewport acceptance.

## Execution Boundaries

- Use only Java 23/Maven and the existing Spring Boot 4/Vue 3 stack unless an evidence-backed compatibility ADR approves a change.
- Keep destructive MySQL tests on their dedicated `stu_stage3_test*` or `stu_stage4_test*` schemas. Use a separately isolated Redis process/port for service outage tests. Do not stop shared services.
- Keep credentials in session-only environment variables; do not record passwords, tokens, or challenge answers in files, screenshots, test output, or handoff logs.
- Preserve the server-side teacher class/course intersection, counselor class scope, student self scope, transaction boundaries, durable revocation, and rate limits.
- Production deployment and production server setup remain out of scope.

## Start Here

1. Re-read `AGENTS.md`, `docs/TASKS.md`, this handoff, and the relevant specification sections.
2. Build a per-ID source/test gap table for Stage 5A and present the CAPTCHA/API behavior design for review before implementing this cross-cutting authentication change.
3. After the design is approved, implement Stage 5A as an isolated vertical slice, run its focused tests against a disposable Redis/MySQL setup, then run the applicable broader suite.
4. Update the task log and requirement matrix with executed evidence. Maintain AOCI once after the final stable changes.
