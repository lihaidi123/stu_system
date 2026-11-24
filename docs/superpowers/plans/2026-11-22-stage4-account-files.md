# Stage 4 Account Files Implementation Plan

> **For agentic workers:** Execute tasks in order. Keep each task independently reviewable and update `docs/TASKS.md` with actual verification.

**Goal:** Deliver bounded XLSX processing plus administrator account template, row import, and credential-safe filtered export.

**Architecture:** Keep workbook parsing in a dedicated utility and account policy in the existing identity/domain service boundary. Account imports use row transactions and existing password/account/student-binding rules; exports use an explicit safe projection and string-cell output.

**Tech Stack:** Java 23, Spring Boot 4, Apache POI 5.4.1, JdbcTemplate, Spring Security, MySQL, JUnit/MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-22-stage4-completion-design.md`; detailed contracts in `docs/开发文档.md` sections 8, 9, and 11 and `docs/STAGE-4-ROADMAP.md`.

## Global Constraints

- Accept real `.xlsx` files only; maximum upload is 10 MiB and maximum data rows is 10,000.
- Keep workbook/file and row report limits enforced server-side; never trust filename or cell values.
- Account file routes are ADMIN-only. Do not return or log passwords, hashes, tokens, raw workbooks, or temporary password values.
- Repeated account identities are skipped; never replace an existing password.
- Student account import binds only an existing unbound student profile and must preserve account/profile identity rules.
- Account import is row-atomic per `docs/开发文档.md`; grade imports remain all-or-nothing through `DomainService.addGradesBatch` in the later grade-files plan.

---

### Task 1: Bounded XLSX Reader and Writer

**Files:**
- Modify: `src/main/java/edu/stusystem/service/SpreadsheetUploadPolicy.java`
- Create: `src/main/java/edu/stusystem/service/XlsxWorkbookReader.java`
- Create: `src/main/java/edu/stusystem/service/XlsxWorkbookWriter.java`
- Test: `src/test/java/edu/stusystem/service/SpreadsheetUploadPolicyTest.java`

**Produces:** Reader opens only validated OOXML workbooks, rejects formulas and malformed/oversized packages, caps data rows at 10,000, reads identifier columns as strings, and closes resources. Writer emits explicit string cells and returns a bounded XLSX byte array. Row-result output is capped to a documented count and message length.

- [ ] Add unit tests for empty file, oversize file, invalid extension/MIME, non-ZIP bytes, malformed ZIP/OOXML, row cap, formula cell rejection, leading-zero identifiers, and text-cell output.
- [ ] Run the focused tests and confirm failures correspond to missing behavior.
- [ ] Implement the bounded reader/writer using POI `WorkbookFactory` and `ZipSecureFile` limits; never inflate during the cheap signature check.
- [ ] Run focused tests and then the existing unit suite.
- [ ] Document error codes, limits, and parsing behavior in `docs/API.md` and `docs/TESTING.md`.

### Task 2: Account Import Domain Operation

**Files:**
- Modify: `src/main/java/edu/stusystem/service/IdentityService.java`
- Modify: `src/main/java/edu/stusystem/service/DomainService.java` only if profile lookup/binding coordination requires it
- Create: `src/main/java/edu/stusystem/service/AccountImportService.java`
- Test: `src/test/java/edu/stusystem/service/AccountImportServiceTest.java`
- Test: `src/test/java/edu/stusystem/service/Stage4AccountFilesMySqlTest.java`

**Produces:** A row report containing `sourceRow`, stable outcome code, and safe message; counts for total/created/skipped/failed. Validate workbook rows and temporary password before writes. For each data row, create the account with the shared `PasswordPolicy`; STUDENT requires an existing unbound profile. Use a transaction per row, translating uniqueness race conflicts to a deterministic skip/conflict without retrying with altered input.

- [ ] Add pure unit tests for required columns, allowed role/status enums, row-number reporting, duplicate rows, safe error output, and no secret fields.
- [ ] Add isolated MySQL tests using only `stu_stage4_test*` for account row commit, student binding, existing identity skip, and concurrent unique-key conflict.
- [ ] Run the new tests red against the current service surface.
- [ ] Implement service transaction boundaries and reuse account creation, password policy, and binding behavior; do not duplicate password hashing or write account SQL around lifecycle rules.
- [ ] Run focused tests and the Stage 4 security integration tests.

### Task 3: Account Template, Import, and Export API

**Files:**
- Modify: `src/main/java/edu/stusystem/api/DomainController.java`
- Modify: `docs/API.md`
- Test: `src/test/java/edu/stusystem/service/Stage4AccountFilesMySqlTest.java`

**Produces:** ADMIN-only `GET /api/v1/admin/accounts/import-template`, `POST /api/v1/admin/accounts/import` with multipart workbook and separate temporary password, and filtered `GET /api/v1/admin/accounts/export`. Export selects all filtered rows, excludes `password_hash` and every secret field, and writes untrusted text as string cells.

- [ ] Add authenticated MockMvc tests for ADMIN access, non-admin denial, XLSX content type/disposition, import report semantics, filters, and credential-field absence from export.
- [ ] Run route tests to observe expected failures.
- [ ] Implement endpoints using the bounded utility and account import service; ensure `@Audited` metadata never captures request body, temporary password, or raw file.
- [ ] Run focused MySQL/MockMvc tests and inspect generated OpenAPI operations.
- [ ] Update `docs/API.md`, `docs/REQUIREMENTS.md`, and `docs/TASKS.md` with the real verified scope.

### Task 4: Account File Acceptance and Maintenance

**Files:**
- Modify: `docs/TESTING.md`
- Modify: `docs/TASKS.md`
- Modify: `docs/API.md`
- AOCI: update only the final machine-issued batch after source and docs stabilize.

- [ ] Run Stage 4 account-file tests against a disposable `stu_stage4_test*` schema and isolated Redis keys.
- [ ] Run Maven test suite and package; record exact counts and environment.
- [ ] Smoke OpenAPI and the account file routes locally without writing credentials to files or logs.
- [ ] Record any remaining QA-15/QA-16 cases as open; do not mark account workflow complete if any required acceptance case is untested.
- [ ] Run AOCI maintenance once after all source/docs changes, complete the current full candidate batch, and preserve its terminal proof.
