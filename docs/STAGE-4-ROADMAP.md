# Stage 4 Roadmap: Imports, Exports, and Personal Center

Updated: 2026-11-23

This roadmap follows Stage 3 and implements section 11.1 item 4 of [开发文档.md](开发文档.md). The source specification defines this stage as file workflows, avatars, profile verification, password-change revocation, and failure-path handling. It does not mean the current product has completed the earlier identity stage: this repository still has open identity/security requirements that Stage 4 depends on.

## Scope

- User/account Excel import and export, including the account template, row-level outcomes, safe retries, and password handling.
- Grade Excel import/export, including all-filtered-record export, strict value validation, row reporting, atomic grade writes, and Stage 3 derived-data synchronization.
- Personal profile read/update, username/contact normalization and uniqueness, availability checks, password verification/change, and credential invalidation.
- Avatar upload, replacement, removal, default restoration, ownership checks, content inspection, storage failure handling, and cleanup behavior.
- API/OpenAPI, data-model, audit, frontend workflow, and QA evidence for the above paths.

## Entry Conditions and Dependencies

Before opening a Stage 4 implementation slice, verify its security dependencies against source and tests. Do not treat the existing login/logout endpoints as completion of the identity foundation.

- The current account-status, role-change, password-change, and administrative reset paths must invalidate old credentials through `auth_version`; re-enabling an account must not restore old tokens.
- The current durable MySQL revocation behavior must fail closed when revocation state cannot be confirmed. Redis failure must not restore revoked credentials.
- Login and sensitive profile/password verification throttling must use atomic counters and expiry, and must fail closed when the counter store is unavailable.
- Authenticated identity must come from the server principal. Profile, password, contact, avatar, account, and grade operations must reject cross-account targets.
- Stage 3 grade transaction and derived-data behavior remains the only supported write path for imported grades; import code must not insert directly around it.
- No credentials, password hashes, complete tokens, private avatar object keys, or raw import file contents may be written to logs, responses, source control, or test evidence.

If an entry condition fails, record the specific blocker and finish independent documentation or non-dependent work. Do not weaken revocation, throttling, or role/data-scope rules to make file or profile workflows pass.

## Work Sequence

### 1. Shared File Handling

Define a server-side file boundary before implementing imports. Set explicit accepted extensions, MIME/content checks, compressed and uncompressed size limits, row limits, and error response limits. Treat filenames and worksheet values as untrusted data. Stream or bound file processing so oversized inputs cannot exhaust memory. Keep temporary files outside tracked source and clean them on success and failure.

### 2. Account Import and Export

Implement the documented administrator endpoints for account template download, row import, and filtered account export. Reuse the same account creation and password policy as interactive account creation. A single administrator-supplied temporary password must never appear in row reports, logs, exports, or audit payloads. Do not export password hashes, tokens, reset material, or secret fields.

Import behavior must be explicit: report source row number, stable result code, and safe message; distinguish created, skipped, and rejected rows. Define whether the account import is row-atomic or file-atomic from the applicable contract before coding. Repeated imports must not overwrite existing accounts or passwords unless the contract explicitly describes an idempotent skip. Concurrent uniqueness conflicts must produce deterministic row outcomes.

### 3. Grade Import and Export

Add filtered grade export that selects every matching authorized record, independent of the current page. Reuse the same scope builder used by grade listing and summary operations: administrators are global, teachers receive only their authorized class/course intersection, counselors only assigned classes, and students only their linked profile where the endpoint permits it. Never trust client-supplied role or scope identifiers.

Validate every row before the first insert where possible, then repeat conflict and authorization checks under the same ordered locks used by Stage 3. Preserve stable student/course/term IDs, credit snapshots, score precision, GPA calculation, duplicate uniqueness, atomic batch semantics, and derived summary/ranking updates. A failure must identify the row safely and leave no partial grade facts or projections. Do not export only the visible page when the user requested the full filtered result.

### 4. Personal Center and Credential Changes

Implement `GET /me/profile`, username/contact updates, availability checks, sensitive password verification, and password change. Normalize username/email/phone consistently across availability checks, writes, and database uniqueness. Missing patch fields mean unchanged; explicit `null` clears nullable contact fields. Validate against the source rules, including raw password input (no implicit trimming), uniqueness, and current-password verification.

Password verification counters must be scoped to the account, atomically limited, and expire according to the specification. Password change increments `auth_version` within the same transaction as the password update; the current and all other prior tokens become invalid immediately. Return a response that requires a fresh login without exposing whether any submitted secret was correct beyond the necessary stable result.

### 5. Avatar Lifecycle

Implement owner-bound avatar metadata and a storage adapter for COS plus the local development adapter. Check declared type, detected content type, image parsing, and size. Generate object names server-side. Upload replacement first, commit the new reference, then clean the old object; if upload or DB update fails, keep the old avatar usable and clean any newly created orphan. Cleanup failure must not undo the new avatar and must leave a safe retry path. Removal restores the default avatar and may only delete the authenticated owner's registered object.

### 6. Frontend and Local Acceptance

Add the account import/export, grade import/export, and personal-center workflows to the existing role UI without exposing privileged actions to non-admins. Present row-level import results, file validation errors, progress, empty states, retryable storage failures, password-change re-login state, and avatar replacement/removal outcomes. Server authorization remains authoritative.

Record selected LeiBox components and sources in `docs/COMPONENT_SOURCES.md`. Review real workflows with ego-browser at 1440x900, 1280x800, 768x1024, and 390x844, and retain screenshots plus issue/fix/recheck notes. These checks are required for the frontend acceptance stage; they have not been performed for Stage 4.

## Acceptance Matrix

- `ADM-04`: account import template, row-level import report, filtered export; no credential material in outputs.
- `ADM-14`: grade export includes every matching row, uses the same server scope as listing, and is independent of pagination.
- `PRO-01..07`: profile read, username/password/contact changes, verification throttling, availability, and avatar lifecycle.
- `SYS-01..02`: stable error semantics and distinct loading, empty, validation, permission, retry, and expired-login states.
- `AOP-04`: successful and failed import/export/profile/avatar operations are audited without request bodies, passwords, tokens, or secrets. A failed business transaction must not leave a success audit.
- `QA-15`: duplicate/invalid/retried account imports have deterministic row reports; a grade import failure is atomic.
- `QA-16`: leading-zero/long identifiers remain strings; invalid numbers are rejected, not coerced to zero; forged or oversized workbooks are rejected.
- `QA-17`: Redis disconnect, app restart, and MySQL unavailability do not restore revoked credentials or silently disable throttling.
- `QA-18`: three failed sensitive-password attempts trigger the documented server limit; direct API password changes still require the old password and invalidate old tokens.
- `QA-19`: oversize/forged image, cross-owner delete, and replacement failure are safely rejected; the old avatar remains available when replacement fails.
- `QA-20`: account/profile binding, username change, and repeated student-account generation preserve identity and never replace an existing password.
- `QA-22`: all new and affected workflows pass the documented viewport/keyboard review with retained evidence.
- `QA-23`: real Spring proxy paths, batch/import paths, failure audit, and secret redaction are verified.
- `QA-24`: filters, totals, statistics, and export selection share the same authorization scope; exports are not page-limited.

## Verification Plan

1. Add fast unit tests for validators, field normalization, safe error mapping, and file-size/type decisions.
2. Add dedicated MySQL tests for uniqueness races, row/file atomicity, credential invalidation, profile transaction rollback, audit outcomes, and avatar metadata ownership. Use only a disposable schema with a `stu_stage4_test` prefix.
3. Add Redis integration tests for one-time challenge consumption and atomic login/profile counters, including Redis outage and expiry behavior. Do not disable limits in tests; use isolated keys/database and controlled clocks where supported.
4. Add authenticated MockMvc coverage for each role, cross-account access, import/export filtering, pagination-independent export, account lifecycle, and stable error responses.
5. Run the full Maven suite and package, then smoke current OpenAPI routes against isolated test services.
6. Run frontend build and ego-browser evidence capture at all four required viewports; update `TASKS.md`, `API.md`, `DATA_MODEL.md`, `REQUIREMENTS.md`, `TESTING.md`, ADRs, and component-source records to match verified behavior.
7. At the final stable source/document state, maintain AOCI once and report only evidence actually collected.

## Out of Scope

Stage 5's complete four-role frontend implementation and broad visual acceptance should not be claimed complete merely because Stage 4 file/profile screens exist. Production deployment remains excluded. Stage 4 began with a security prerequisite slice on 2026-11-22. Role/status/password invalidation, current-role token validation, Redis login windows/locks, connection-failure responses, and Redis state across Spring context recreation pass dedicated MySQL/MockMvc/Redis tests and the Stage 3 regression suite. A full OS-process restart and shared Redis disconnect were not performed. Shared file handling and account-file workflows can proceed independently; personal-center password verification still needs its own fail-closed limiter before implementation.

## Stage 4 Scope Clarification (2026-11-22)

The user clarified that this development does not include a mobile client. Stage 4 UI work and requested ego-browser evidence are desktop only at 1440x900 and 1280x800. This narrows new Stage 4 visual acceptance; it does not claim the existing responsive CSS is removed or that the broader four-role Stage 5 design requirement is complete. The 768x1024 and 390x844 Stage 4 captures are not applicable to this scoped work.

## Implementation Status (2026-11-22)

Account/grade workbook backend routes, sensitive-password Redis throttling, profile/contact updates, and local/COS avatar storage paths are implemented. Desktop UI screenshots and isolated Stage 4 service tests exist. The dedicated Stage 4 MySQL/MockMvc suite passed 22 tests and applied V4/V5. The broader Maven suite passed against the isolated Stage 3 schema; its 22 Stage 4 DB tests skipped by design. Stage 4 is not fully accepted until resource-attack fixtures, shared Redis service disconnect and interactive real-API ego-browser checks run.

## Final Acceptance (2026-11-23)

Stage 4 is accepted for desktop scope. A fresh isolated schema passed all 24 Stage 4 MySQL cases and migrated V1-V5. The complete Maven run passed 92 tests with 0 failures/errors and 22 skips (21 Stage 3 schema-guard skips and the separately executed opt-in Redis outage case). Separate acceptance verified a live isolated Redis process disconnect and a revoked token rejected after a full Java application-process restart. Authenticated real-API ego-browser workflows covered account/grade import and export, profile/contact/username/password and avatar operations at 1440x900 and 1280x800. See `docs/TASKS.md`, `docs/TESTING.md`, and `docs/evidence/stage4/README.md` for exact evidence. Broader CAPTCHA, four-role, and QA-22 acceptance remains outside Stage 4.
