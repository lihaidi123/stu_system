# Stage 4 Completion Design

## Goal

Complete Stage 4 as specified by `docs/STAGE-4-ROADMAP.md` and sections 8, 9, 11.1, and 11.2 of `docs/开发文档.md`: account and grade workbook workflows, personal profile and credential changes, avatar lifecycle, frontend flows, and evidence-based acceptance.

## Current Boundary

The existing service already has administrator account lifecycle operations, a shared login failure limiter, server-derived principals, grade batch writes, scoped grade queries, transactional GPA/summary/ranking derivation, and `auth_version` invalidation. Stage 4 currently adds an XLSX upload policy and administrator-only account template endpoint. Workbook import/export, self-service profile routes, sensitive-password throttling, avatar storage, and corresponding frontend flows are still incomplete. The current overview attestation passed 10/10, while AOCI governance was dirty before the latest five-entry maintenance batch; implementation claims remain source-bound.

## Decomposition

### A. Shared Files and Account Workflows

Create a bounded XLSX processing boundary that enforces actual multipart byte limits, extension and declared MIME rules, OOXML package parsing, decompression limits, a 10,000-row cap, bounded row reports, and cleanup of any temporary resources. Keep parsing independent from account business rules.

Add administrator account template, row-atomic import, and filtered export. The import receives one temporary password separately from the workbook, validates all workbook rows before writes where possible, and calls existing account creation/password-policy/student-binding paths. Existing identities are idempotent skips; malformed data, missing student profiles, and binding conflicts are row-level failures. Passwords, password hashes, tokens, and temporary storage keys are excluded from every result and audit event. Uniqueness races become deterministic skipped/conflict outcomes without overwriting account passwords.

### B. Sensitive Verification, Personal Center, and Avatar

Add an account-scoped Redis atomic limiter for sensitive-password verification: three failed checks lock that account for five minutes; Redis unavailability fails closed. Password change itself repeats current-password verification and limiter enforcement, changes the password and increments `auth_version` in one MySQL transaction, then requires a fresh login. Profile identity is always read from `CurrentUser`; no caller-supplied account ID selects the target. Normalize and check username/contact values using the same rules as writes and database uniqueness.

Add an avatar storage port with a local-development adapter and Tencent COS adapter. Validate size, declared and detected type, and full image decoding. Generate object keys server-side. Replacement uploads a new object before committing its owner-bound reference; on DB failure remove the new orphan and preserve the old object. After commit, old-object cleanup is retryable and cannot roll back the active avatar. Removal affects only the authenticated owner’s recorded object and restores the default avatar.

### C. Grade Files, Frontend, and Acceptance

Add grade workbook import through the existing `addGradesBatch` transactional path. Parse identifiers as strings, resolve student/course/term by stable IDs or unique documented names/codes, reject formulas and invalid numeric values, and report source rows with a bounded safe error list. Any invalid/conflicting/unauthorized row rejects the whole grade batch without partial grade or derived-data writes.

Add grade export using the same role-scoped filter builder as grade listing, without pagination. Account export remains administrator-only and omits all credentials. Workbook output writes user text as explicit string cells to prevent formula execution.

Extend the existing Vue role workbench for administrator account files, administrator/teacher grade files, and authenticated profile/avatar operations. The UI consumes server reports and stable errors, shows progress/error/empty/re-login states, and never implements authorization as a client-only rule. Record LeiBox sources and capture ego-browser evidence at 1440x900, 1280x800, 768x1024, and 390x844.

## Contracts and Safety

- Keep Spring Boot 4, Java 23, POI, COS, Redis, OpenAPI, and existing dependencies; any compatibility change requires a verified ADR.
- Preserve administrator-only account file operations and teacher grade file scope as the intersection of authorized classes and courses. Counselors and students cannot use grade import/export.
- Do not bypass `IdentityService` account creation, `PasswordPolicy`, student binding, `DomainService.addGradesBatch`, GPA proxy, transaction derivation, audit redaction, or optimistic/uniqueness constraints.
- Audit only action/target/outcome metadata. Never include request bodies, raw files, passwords, hashes, tokens, private COS keys, or row values that contain sensitive data.
- Input errors use stable codes and bounded messages. No endpoint returns unbounded cell text or stack details.
- Avatar storage unavailability returns an explicit retryable error without exposing credentials or object keys.

## Data and Interface Plan

Prefer no schema migration for workbook workflows and username/contact availability unless inspection proves the existing account schema cannot enforce the source contract. Avatar metadata requires an owner-bound database reference and migration if no suitable columns exist. Keep storage identifiers private; return only a safe avatar URL or application-owned opaque reference.

The required account routes are `GET /api/v1/admin/accounts/import-template`, `POST /api/v1/admin/accounts/import`, and `GET /api/v1/admin/accounts/export`. Grade file routes are `GET /api/v1/grades/import-template`, `POST /api/v1/grades/import`, and `GET /api/v1/grades/export`. Profile routes follow the source specification: `GET/PATCH /api/v1/me/profile`, username/contact availability checks, sensitive password verification/change, and owner-bound avatar upload/removal. Exact payloads and stable response codes will be transcribed to `docs/API.md` before each route is implemented.

## Implementation Order

1. Finish shared bounded workbook parsing/response utilities and tests.
2. Complete account import/export around existing account lifecycle operations.
3. Add sensitive-password Redis limiter and tests before any self-service password route.
4. Add profile and availability operations with schema changes only if required.
5. Add avatar metadata, local/COS adapters, lifecycle recovery tests, and API.
6. Add grade workbook template/import/export through existing scoped query and batch-write paths.
7. Add Vue workflows, source records, accessibility/responsive review, and evidence.
8. Run focused unit tests, isolated MySQL and Redis integration tests, full Maven suite/package, OpenAPI smoke checks, frontend build, and four viewport browser review. Update requirement and task matrices only from executed results; run AOCI maintenance once the final source and docs are stable.

## Failure and Transaction Behavior

Account import commits each valid row independently as the source contract requires, while each row’s account creation and optional student binding is atomic. Existing/repeated rows never update passwords. Grade import validates before writes and then delegates the full set to the existing single-transaction batch method. Profile password update and `auth_version` increment share one transaction. Avatar storage and database references use explicit compensating cleanup; cleanup failure leaves a retry record and does not change which object is active.

## Acceptance Evidence

Stage 4 is complete only when ADM-04, ADM-14, PRO-01..07, SYS-01..02, AOP-04, QA-15..20, QA-22..24 have implementation and corresponding executed evidence. Existing Stage 3/selected Stage 4 evidence remains valid only for unchanged behavior. Full Stage 4 is not complete based on compilation or route presence alone.

## Self-Review

- The three workstreams cover the Stage 4 roadmap, including security prerequisites and visual acceptance.
- Account row atomicity and grade file atomicity follow the source contract; no import overwrites existing credentials.
- Sensitive-password throttling precedes self-service password operations; server identity and existing role scope remain authoritative.
- Storage recovery does not sacrifice the old avatar or expose private object keys.
- Tests and UI evidence are explicit deliverables; no existing test result is presented as evidence for new behavior.
