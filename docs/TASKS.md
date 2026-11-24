# Development Status

Updated: 2026-11-24

## Independent Frontend Rewrite Preview (2026-11-24)

- Added `frontend/src/NewWorkspace.vue` and `frontend/src/new-workspace.css` as a separate role-aware desktop workspace. Existing `App.vue`, `main.js`, and active `DashboardView.vue` remain the application entry path; the new workspace has not been substituted into the app.
- The preview binds the existing `/api/v1` contracts for role-scoped analytics, students, grades, options, account workbooks, profile, password, contact and avatar operations. It adds explicit table columns, server pagination/search, stale-request suppression, empty/error/loading feedback, role navigation, grade filters, administrator teaching data forms, file actions and profile flows. Client-side role selection is presentation only; backend authorization remains authoritative.
- Added `frontend/new-workspace-preview.html`, `frontend/src/new-workspace-preview.js`, and an isolated Vite config for preview/build without changing the normal entry or output directory. Preview URL: `http://127.0.0.1:5174/new-workspace-preview.html`.
- LeiBox was searched for Vue navigation candidates; no candidate fit dense data workflows, so existing Element Plus remains in use. See `docs/COMPONENT_SOURCES.md`.
- Verification performed: `npm exec vite -- build --config vite.new-workspace.config.js` passed and compiled the new SFC. Latest isolated preview bundle is 521.29 kB JS; Vite reports the >500 kB chunk warning. `git diff --check` passed. ego-browser 2.2.1 checked ADMIN/TEACHER/COUNSELOR/STUDENT navigation, ADMIN student empty state, removed topbar, right-side avatar logout menu, profile name/logout placement, role-label removal from current identity, color/radius states and bottom-filling workspace. Screenshots at 1280x800, 1440x900 and 1920x1080 plus role/profile views are under `docs/evidence/new-workspace/`. `document.scrollWidth` equaled `clientWidth` at all three desktop sizes. Browser checks used a localStorage-only presentation role marker, no token or mocked API responses; authenticated backend data and action flows were not exercised. Evidence scope is detailed in `docs/evidence/new-workspace/README.md`.
- Current limitations: local Vite port 5173 was unavailable, no existing authenticated session or test credentials were available, and backend API data cannot be validated in this run. Browser review covered the role shell and empty state, not real populated tables, file operations, profile writes, all-role authorization, or keyboard completion of full workflows. Mobile-specific layout was not added per the desktop-only UI skill.
- Next: user reviews the independent preview and authorizes replacement; then wire it through the app entry, fix any review findings, run an authenticated real-API ego-browser pass with an isolated account, and recheck requirements/evidence. No production deployment is included.
- Visual feedback follow-up (2026-11-24): removed the full topbar; the page-title row has a right-side avatar with a click-to-open logout menu. The transparent sidebar shows the signed-in name as the profile link, with a light divider above it; current-user role labels are removed from overview/profile. Main white workspace reaches the viewport bottom with square lower corners. Radii: 16px outer, 14px inner panels, 12px controls/menu, 8px menu item. Enabled primary buttons are blue with white text; disabled primary buttons are light blue. Active nav uses bold text and a neutral mask only. ego-browser checked menu/logout behavior, profile identity, divider, main surface bounds, radius/color values and no horizontal overflow at 1280x800, 1440x900, 1920x1080. Updated captures are in `docs/evidence/new-workspace/`.
- Workflow feedback follow-up (2026-11-24): teaching-data create actions are separated into horizontal scrolling buttons above the department directory; each opens a second-level form dialog, and a tooltip-backed, icon-only ellipsis opens all action choices. Errors now use fixed top-right toast notifications, expire after 3 seconds and lightly stagger when stacked. ego-browser opened the action row, department dialog and overflow menu; a remote student-option failure produced the toast and it disappeared within the 3.2-second observation. Build passed; latest preview JS is 534.38 kB and Vite warns above the 500 kB threshold. Evidence: `docs/evidence/new-workspace/foundation-actions-1440x900.png`, `foundation-dialog-1440x900.png`.

## Remaining Work Summary (2026-11-23)

- Stage 4 account/grade files, profile/password, avatar, security failure paths, audit redaction, and desktop workflows reached acceptance on 2026-11-23. Verification details and remaining broader product work are in `docs/REMAINING-WORK.md`, `docs/TESTING.md`, and `docs/evidence/stage4/README.md`.
- Added [NEXT-STAGE-HANDOFF.md](NEXT-STAGE-HANDOFF.md) as the implementation entry point for the remaining product scope. It separates the one-time CAPTCHA slice, four-role workflow parity, and QA-22 review, with concrete requirements and acceptance evidence.
- Acceptance used disposable schema `stu_stage4_test_20261123codex` and temporary Redis port 16379. Those targets and isolated API/UI processes were removed after verification; `stu_system`, shared MySQL/Redis, and the pre-existing Vite server were left untouched.
- The broader product remains partial: CAPTCHA lifecycle, complete four-role workflows, QA-22's four viewport/keyboard review, and remaining requirement IDs are not claimed complete.
- Next: obtain approval for the decomposed broader product design, then implement the remaining requirement groups in scoped stages. Production deployment remains out of scope.

## Next Stage Handoff (2026-11-23)

- Created `docs/NEXT-STAGE-HANDOFF.md` from the current specification, API/data/design contracts, requirements matrix, and verified Stage 4 evidence.
- The document records the current accepted baseline, remaining AUTH/ADM/PRO/SYS/AOP/QA gaps, a proposed Stage 5A/5B/5C sequence, test isolation and secret-handling constraints, and a concrete start point for CAPTCHA design review.
- This turn is documentation-only. No code behavior, tests, database, or browser session was changed or run for the handoff itself. The Stage 4 verification remains as recorded above; no broader product requirements are claimed complete.
- Next: review/approve the Stage 5A CAPTCHA/API behavior design, then proceed with its implementation and focused isolated verification.

Stage 3 (Grade and Consistency) is complete for its defined scope. User/Excel import subcases of QA-15 and listed later phases remain deferred. See [STAGE-3-REMAINING-HANDOFF.md](STAGE-3-REMAINING-HANDOFF.md) for acceptance evidence.

## Implemented

- Grade create, batch create, update and delete with server GPA, credit snapshots, optimistic versions, transactional student-term summaries and class-term ranking projections.
- GPA calculation is intercepted through the Spring proxy by `GradeGpaAspect`; grade derivation and census synchronization run as `BEFORE_COMMIT` transactional listeners in `GradeDerivationAspect`.
- Student create/batch/edit/transfer/delete and explicit account binding; class census events cover lifecycle writes and rebuild. Profile identity changes invalidate linked account credentials.
- Scoped grade/student pagination and details, term/all-term rankings, dashboards, class statistics, derived-data rebuild and redacted audit query.
- Added MySQL/MockMvc cases for GPA proxy activation, lifecycle HTTP routes, teacher class/course intersection, counselor and student detail/ranking scope, pagination totals/limits, stale versions, batch row reporting, multi-term SQL, zero-credit/empty data and lifecycle consistency.
- Lock order and transaction boundaries recorded in [adr/0002-grade-consistency-locking.md](adr/0002-grade-consistency-locking.md).

## Verification

- `mvn -q clean`: exit 0.
- `mvn -q test`: exit 0; 28 tests passed, 0 failures/errors/skips, including all 21 `GradeConsistencyMySqlTest` cases against dedicated schema `stu_stage3_test_20261120`.
- `mvn -q -DskipTests package`: exit 0 after the latest code and test changes.
- `mvn -q '-Dtest=GradeMetricsTest,GradeRankingTest,GradeGpaAspectTest' test`: exit 0; 7 tests passed, including GPA thresholds and Spring proxy verification.
- Java 23.0.1 and Maven 3.9.13 are available.
- Local app smoke ran with the `test` profile against the isolated schema on port 8181: `/actuator/health` returned `UP`; `/v3/api-docs` returned OpenAPI 3.1.0 and contained the student pagination and administrator routes.
- Flyway validated all three migrations and reported schema version 3 current. It warned MySQL 9.6 exceeds its verified version 8.1.
- No ego-browser visual review applies to Stage 3 scope.

## Deferred / Remaining Product Work

- QA-15 user import and Excel import subcases are explicitly deferred to later file/import work; no import coverage is claimed.
- CAPTCHA and sensitive-password verification throttling, remaining account lifecycle, personal center, Excel/COS, full frontend, LeiBox review and ego-browser acceptance remain later phases.
- Test credentials must remain in secure, session-only environment variables and must not be recorded in project files.

The dedicated local MySQL connection target and PowerShell test invocation are recorded in [STAGE-3-REMAINING-HANDOFF.md](STAGE-3-REMAINING-HANDOFF.md).

## Next Step

Stage 3 acceptance is closed for its recorded scope. Stage 4 has started with the security prerequisite slice described below; imports/exports and personal center are not implemented.

## Stage 4 Progress (2026-11-22)

- Added administrator role, status, and password-reset operations. Actual changes increment `auth_version`; role changes remove stale teaching grants; bound student accounts cannot change role without explicit unlink; the last active administrator cannot be disabled or demoted.
- Bearer validation now checks current database role, status, `auth_version`, and MySQL revocation in one query. Database unavailability fails closed with HTTP 503.
- Login failure limiting now uses Redis atomic increments with a 15-minute failure window and a separate 15-minute lock beginning at failure five. Known username/business-number aliases share the account-ID counter; unknown identifiers are represented by SHA-256 keys. Redis failure returns HTTP 503.
- Account creation, bootstrap, and password reset share the 8–20 character / three-of-four character-category policy. Password reset also rejects username equality and reuse of the current password.
- Updated API, data-model, requirements, and Stage 4 roadmap records to distinguish source implementation from verified acceptance.

### Current Verification

- `mvn -q -DskipTests compile`: exit 0 on 2026-11-22. This compiled production sources only; no tests were run for this slice.
- `mvn -q '-Dtest=AtomicRateLimitServiceRedisTest,Stage4AccountSecurityMySqlTest,BearerTokenFilterTest' test`: exit 0; 14 tests passed on 2026-11-22 against live local Redis and `stu_stage4_test_20261122`.
- `mvn -q test` with the documented Stage 3 URL: exit 0; 34 tests passed, 0 failures/errors. Eight Stage 4 MySQL tests skipped under this Stage 3 URL and were run separately against the Stage 4 schema.
- Tests cover Redis counter/lock expiry, alias sharing, fifth-failure lock, refused Redis connection, Spring application-context restart persistence, refused MySQL connection at the bearer filter, current role/version/token rejection, administrator-only route, account status/password revocation, grant cleanup, student binding, and serial/concurrent last-admin protection.
- Shared Redis and MySQL servers were not stopped. The Spring context was closed and recreated against the same Redis server; a full OS-process restart and shared Redis disconnect remain unverified.

### Remaining / Blockers

- Verify an OS-process restart and shared Redis disconnect if exact QA-17 acceptance is needed; isolated connection-refusal and Spring application-context restart cases passed.
- Sensitive-password verification throttling and captcha remain prerequisites for personal-center work. Shared file handling, account import/template/export, and grade import/export remain unimplemented; avatar lifecycle, frontend workflows, LeiBox source review, and ego-browser evidence also remain open.

## Next Step

Proceed with shared file handling and account/grade file workflows. Keep personal-center implementation gated on sensitive-password verification throttling; exact OS-process and shared Redis outage checks remain optional QA-17 follow-up evidence.

## Stage 4 Update (2026-11-22)

- Implemented the shared spreadsheet upload policy in `SpreadsheetUploadPolicy`: rejects empty/oversized files, requires `.xlsx` and XLSX MIME, checks the ZIP package signature without inflating untrusted content, and configures Apache POI ZIP entry/text limits and inflate-ratio protection.
- Recorded the 10 MiB upload and 10,000 data-row contract in `docs/API.md`. This policy is not yet connected to an endpoint; actual OOXML parsing, row-count enforcement, bounded row reports, temporary storage, and import/export workflows remain open.
- AOCI rules and overview were requested. Overview chunk 1 of 2 was received; continuation failed with `overview_cursor_chain_mismatch` due to a submitted cursor mismatch. Per repository instructions, the overview chain was stopped without retry or semantic attestation. Subsequent implementation is source-bound.
- Verification: no compile or tests were run for this update. No frontend or ego-browser review was performed.
- Remaining: wire the policy into file endpoints, implement bounded workbook parsing and row limits, then account import/export and grade export/import. Before personal-center work, implement sensitive-password verification throttling.
- Next: implement the account workbook template and row import on the shared policy, after verifying the account-creation and student-binding transaction paths; maintain AOCI once at the final stable state.

## Stage 4 Continuation (2026-11-22)

- Added `GET /api/v1/admin/accounts/import-template`, guarded by ADMIN, returning an XLSX header row with the seven documented account columns and no sample credentials or user data.
- Updated `docs/API.md` to distinguish this completed template route from the still-unimplemented account import/export and grade file routes.
- AOCI full overview completed: 62/62 entries, 2 chunks, Challenge 10/10. Governance remains dirty/stale and full-system cognition is not reliable; source-bound work continues.
- Verification: no compile or tests were run in this continuation. No browser review was performed.
- Remaining: template route still needs automated authorization/content-type/workbook tests; import routes must reuse account creation and student binding rules, enforce row outcomes and protect credentials. File-policy behavior remains untested and not yet connected to an upload route.
- Next: implement the account import route after establishing an atomic/safe row creation API; then verify with isolated test fixtures and update requirement evidence.

## Stage 4 File Workflow Progress (2026-11-22)

- Shared XLSX processing now validates OOXML via POI, caps compressed uploads at 10 MiB, rows at 10,000, columns at 64, output text at 8 MiB, output cells at 100,000, entry/text expansion at 10 MiB, and rejects formulas/error cells. Workbook rows preserve source row number and cell type; writer emits text cells.
- Added Flyway V4 nullable unique email/phone fields. Account creation now normalizes contact values, accepts documented active/disabled state and role values, and checks ADMIN server-side. Account workbook template/import/export endpoints now exist; import is row-atomic and skips existing identities without replacing passwords.
- Added grade workbook template/import/export routes. Grade import resolves stable IDs and invokes `addGradesBatch` once; export uses the shared `gradeQuery` scope builder and is independent of pagination.
- Verification executed: `mvn -q '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest,XlsxWorkbookWriterTest,AccountImportServiceTest,GradeImportServiceTest,Stage4AccountSecurityMySqlTest' test` exited 0. The unit tests ran successfully. The guarded Stage 4 MySQL class was skipped because `MYSQL_TEST_URL` is unset. No Redis or UI review was run in this update.
- Remaining: run the new Stage 4 MySQL tests and V4 migration on an isolated `stu_stage4_test` schema; add/check export route behavior and concurrency; implement sensitive-password verification limiter, profile/contact operations, avatar lifecycle, frontend workflows, visual evidence, full suite/package/OpenAPI smoke.
- Next: implement account-scoped sensitive-password verification throttling with Redis fail-closed behavior and isolated expiry tests before profile/password endpoints.

## Stage 4 Profile Progress (2026-11-22)

- Added account-scoped sensitive-password verification using the existing Redis atomic limiter: the third consecutive failure creates a five-minute lock; Redis failure returns 503. Correct verification clears the account failure/lock keys. Password changes repeat verification and update `password_hash` plus `auth_version` in one transaction.
- Added authenticated profile read, username/contact update, availability, password verification, and password change routes. Username changes increment `auth_version`; email is trimmed/lowercased, phone is validated as an 11-digit mainland-China number, and explicit null clears contacts.
- Added Stage 4 MySQL/MockMvc cases for profile field redaction, contact normalization/null clearing, username/password token invalidation, and three-failure lock behavior. These remain unexecuted because `MYSQL_TEST_URL` is not set.
- Verification: `mvn -q '-Dtest=SensitivePasswordVerificationServiceTest,AtomicRateLimitServiceRedisTest,Stage4AccountSecurityMySqlTest' test` exited 0 for executable unit/Redis cases; the Stage 4 MySQL class was skipped by its environment guard.
- Remaining: avatar storage/metadata/recovery, account/grade export integration tests, local frontend workflows and visual evidence, full isolated Stage 4 suite.
- Next: design and implement owner-bound avatar storage adapters and failure-safe replacement/removal.

## Stage 4 Avatar Progress (2026-11-22)

- Added actual image decoding validation for JPEG, PNG, GIF, and WebP; checks MIME against detected bytes, caps upload size at 2 MiB and decoded pixel area, and does not use the file extension as a trust signal. WebP uses TwelveMonkeys ImageIO 3.15.3.
- Added local temporary-directory and Tencent COS storage adapters behind one interface. COS credentials remain environment/configuration inputs and are never returned to clients.
- Added Flyway V5 owner-bound avatar metadata and cleanup retry queue. Replacement stages a cleanup record before upload, commits the new reference before old-object deletion, and retries cleanup asynchronously. Removal deletes only the authenticated owner's stored object reference. Profile response exposes an application route, not the storage key.
- Added unit tests for MIME/content/size, WebP decode, local path safety, replacement upload failure, and cleanup retry behavior; added a guarded Stage 4 MySQL/MockMvc case for owner-bound read/remove.
- Verification executed: `mvn -q '-Dtest=AvatarImageValidatorTest,LocalAvatarStorageTest,AvatarServiceTest,Stage4AccountSecurityMySqlTest' test` exited 0. Unit tests ran; the guarded MySQL class was skipped because `MYSQL_TEST_URL` is unset.
- Remaining: run Flyway V4/V5 and all integration cases against an isolated Stage 4 schema; complete Vue workflows, LeiBox source record, ego-browser screenshots, and full acceptance suite.
- Next: implement the Vue account/grade file and personal-center workflows, then run Stage 4 integration and visual acceptance when isolated services are configured.

## Stage 4 Desktop UI and Evidence (2026-11-22)

- Added ADMIN account-file workspace with template download, filtered credential-safe export, temporary password entry, upload progress, aggregate row outcomes, and per-row status table.
- Added ADMIN/TEACHER grade-file workspace with template, query filters (keyword, term, course, ADMIN class, score range), full filtered export, upload progress, atomic-import error feedback, and retained manual grade entry.
- Added authenticated personal-center workflow for profile display, current-avatar preview/upload/removal, username update, email/phone availability and updates, sensitive-password verification, and password change with re-login state.
- Fixed student option loading to read the paginated `items` envelope; account table now filters locally and displays contact columns independent of the first row values.
- User clarified that Stage 4 does not include a mobile client. Stage 4 UI work/evidence is desktop-only; existing responsive behavior remains, but no new mobile-specific layout is claimed.
- LeiBox live catalog searches found no suitable admin table/upload component; two file-card/animation candidates were rejected as mismatched. Existing Element Plus components are used. 12ui installation remained silent and was stopped twice.
- ego-browser `2.2.1` captures: login and account/grade/profile workflows at 1440x900; account/grade/profile at 1280x800. Logged-in screens use only a browser-session static fixture and demonstrate layout/interaction structure, not backend acceptance. See `docs/evidence/stage4/ego/`.
- Verification: latest `npm run build` exited 0; main JavaScript chunk is 1,129.74 kB and Vite emitted its >500 kB warning. Latest focused Maven tests passed with the Stage 4 MySQL class skipped because `MYSQL_TEST_URL` is unset. Full suite/package and actual Flyway V4/V5 application remain unverified.
- Remaining/blockers: no isolated `stu_stage4_test` MySQL URL for migration, account/grade export/import, profile and avatar integration tests; no API smoke against live Stage 4 services. QA-17 shared Redis disconnect/process restart remains open from prior evidence. Mobile client work is out of scope per user clarification. No production deployment is included.
- Next: run the full Maven suite and package, refresh screenshots after final UI changes, then maintain AOCI once at the final stable state. Stage 4 cannot be marked complete until required backend acceptance runs against isolated services.

## Stage 4 Verification Update (2026-11-22)

- Corrected the test URL builder after the first integration attempt exposed a malformed timezone segment (`Asia/stu_stage4_test...`). No test code or application code was changed for that connection setup issue.
- `mvn -q '-Dtest=Stage4AccountSecurityMySqlTest' test` then passed 22/22 on `stu_stage4_test_20261122`. Flyway validated all five migrations and applied V4 account contacts and V5 avatar metadata/cleanup tables. Coverage includes XLSX template, account import idempotence/concurrent uniqueness and route scope, filtered safe account export, transactional grade import/derived rows, 25-row full grade export, grade-role scope, profile/contact availability, credential invalidation, sensitive password lock, avatar ownership/read/remove/cleanup retry, a separate-JVM Redis lock check, and OpenAPI operations.
- `mvn -q test` with `stu_stage3_test_20261120` exited 0: 82 tests, no failures/errors, with 22 Stage 4 MySQL tests skipped by their schema guard. Together with the dedicated Stage 4 run, all 82 unique cases passed across the two isolated DB runs.
- `mvn -q -DskipTests package` exited 0. `npm run build` exited 0; Vite still warns that the main JS chunk is 1,129.63 kB (>500 kB).
- Spring multipart request limits now match the documented 10 MiB file / 11 MiB request boundary; framework rejections map to stable HTTP 413 `FILE_TOO_LARGE`.
- Ego-browser desktop evidence was captured at 1440x900 and 1280x800 with a browser-only static API fixture; it does not exercise backend behavior. Screens and issue/fix notes are in `docs/evidence/stage4/ego/` and `docs/evidence/stage4/README.md`.
- User scope clarification: Stage 4 development is desktop-only; no mobile-specific view work or mobile screenshots are claimed.
- Remaining acceptance: ZIP-bomb/resource fixtures, shared Redis disconnect and OS-process restart, and an interactive real-API ego-browser flow. Export row overflow and account uniqueness contention are now tested. OpenAPI operations are asserted in MockMvc and the isolated local smoke returned health UP with required paths in `/v3/api-docs`. These gaps still prevent claiming every Stage 4 failure-path acceptance item complete.

## Stage 4 Handoff (2026-11-22)

- Created [STAGE-4-HANDOFF.md](STAGE-4-HANDOFF.md) as the continuation entry point, consolidating the current implementation scope, executed verification, desktop screenshot evidence, remaining acceptance, and next steps.
- The handoff includes a reserved frontend optimization section for the user to extend, with fields for goals, pages/interactions, acceptance criteria, and follow-up evidence. Current measured issue: Vite main chunk is about 1,129.63 kB; route-level/dynamic imports are a candidate for later evaluation, not an implemented change.
- Scope clarification retained: Stage 4 frontend review is desktop-only; the requested ego full-screen captures are visual evidence using a static browser fixture, not real-API acceptance.
- No code, tests, build, or browser review were run in this documentation-only continuation. The Stage 4 acceptance gaps above remain open.
- Next: resume from the handoff, follow current AOCI session rules, finish ZIP resource-abuse tests and isolated-service failure-path/API browser verification, then revisit frontend optimization with the user's additions.

## Stage 4 Frontend Optimization Scope (2026-11-23)

- Filled the frontend optimization section in `docs/STAGE-4-HANDOFF.md` using development spec §10.2 and the existing §10.4/QA-22 requirements: concise workspaces, modern fit-for-purpose Vue/Element Plus patterns, account/grade/profile priorities, accessibility and state coverage, measured chunk-splitting evaluation, LeiBox source recording, and ego-browser review criteria.
- Scope remains desktop-only for this Stage 4 optimization; broader QA-22 retains its four viewport requirement. The handoff separates the two explicitly.
- Verification: documentation-only edit. Read the handoff, `docs/TASKS.md`, `docs/开发文档.md` §10.2–10.4, `docs/DESIGN.md`, `docs/COMPONENT_SOURCES.md`, and Stage 4 evidence notes. No frontend build, code tests, or ego-browser review was run.
- AOCI: rules loaded; full Overview chunk 1/2 arrived, but continuation returned `overview_cursor_invalid` after a cursor submission mismatch. Per session contract, stopped the chain without retry or attestation. This documentation-only change does not require AOCI maintenance.
- Remaining: implement the recorded UI optimization, assess build chunk sizes, review the running local pages with ego-browser and retain evidence, then update this handoff/TASKS with actual outcomes. Existing Stage 4 failure-path and real-API browser acceptance gaps remain open.

## Stage 4 ZIP Resource-Abuse Update (2026-11-23)

- Added an explicit Apache POI ZIP entry-count ceiling of 256 in `SpreadsheetUploadPolicy`, alongside the existing 1% inflate-ratio and 10 MiB single-entry/text limits. Updated `docs/API.md` with the package entry ceiling.
- Added generated-workbook fixtures for a package exceeding the ZIP entry ceiling and a highly compressible workbook part. Both are rejected through the stable `FILE_CONTENT_INVALID` response. The entry-count test was first run against the prior implementation and failed because the valid 260+ entry workbook was accepted; after the limit was added it passed.
- Verification: `mvn '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest,XlsxWorkbookWriterTest,AccountImportServiceTest,GradeImportServiceTest' test` exited 0; 15 tests, 0 failures/errors/skips. Mockito/JDK dynamic-agent warnings were emitted by the existing AccountImportServiceTest run.
- Full suite: `mvn test` exited 0; 84 tests, 0 failures/errors, 43 skipped. Stage 3's 21 and Stage 4's 22 MySQL integration cases skipped because the `MYSQL_TEST_*` values were absent; Redis and unit tests ran. Existing Mockito dynamic-agent warnings appeared.
- Final focused rerun after making the ZIP bomb fixture exceed the 256-entry limit (rather than merely equal it): `mvn '-Dtest=SpreadsheetUploadPolicyTest' test` exited 0; 6 tests, 0 failures/errors/skips.
- Scope limit: this does not prove a package-wide aggregate decompressed-byte ceiling. No MySQL integration run, Redis outage/restart run, frontend build, or browser review was performed in this update. MySQL and Redis listener processes are present locally, but no isolated service-disconnect target was identified. ego-browser tools are not available in this session.
- Remaining: verify aggregate package resource bounds if required by the acceptance criterion; run shared Redis outage and full app process restart only against isolated services; perform authenticated real-API desktop browser review when ego-browser and isolated credentials/services are available; complete the recorded desktop frontend optimization and recapture evidence.
- Next: establish/confirm an isolated Stage 4 MySQL/Redis configuration without touching shared services, then execute the highest-risk pending QA cases and preserve their actual evidence. Stage 4 remains partial.

## Stage 4 Frontend Bundle Update (2026-11-23)

- Replaced full Element Plus plugin installation with registration of the 17 components used by `App.vue`. Existing Element Plus CSS and all screen workflows remain in place; no dependency was added.
- LeiBox query `Vue admin data table filters navigation and upload feedback` (Vue, limit 3) returned navigation animations (`fluid-tooltip`, `glass-tab-menu`, `liquid-glass-navigation`); none fit the dense desktop admin workflows, so no component was adopted. `docs/COMPONENT_SOURCES.md` records the search and decision.
- Build baseline: 1,129.63 kB JS / 371.47 kB CSS. Final `npm run build`: 535.88 kB JS (186.31 kB gzip) / 371.47 kB CSS (50.59 kB gzip), exit 0. JS decreased by 593.75 kB (52.6%); Vite's 500 kB warning remains by 35.88 kB.
- A `manualChunks` experiment that isolated all Element Plus modules created a 1,034.30 kB Element Plus chunk and retained the warning; that config was removed. It did not remain in the final source.
- No frontend tests or ego-browser review were run. The current browser toolset did not include ego-browser, and no authenticated isolated API configuration is available. The built UI behavior is therefore not claimed visually reaccepted.
- Remaining: consider extracting role workspaces into lazy-loaded components to split the remaining JS; run ego-browser at the required desktop sizes and exercise the real API when isolated services and credentials are available. Stage 4 remains partial.

## Stage 4 Combined XLSX Expansion Budget (2026-11-23)

- Added a 64 MiB cumulative actual ZIP expansion limit and a stable `FILE_EXPANDED_TOO_LARGE` error. Preflight uses an 8 KiB buffer and counts bytes while streaming each member; it aborts as soon as the package or a 10 MiB member exceeds its budget, before POI opens the workbook. The shared `XlsxWorkbookReader` invokes the preflight for both account and grade imports.
- Added a valid OOXML fixture with eight extra parts, each 8.5 MiB expanded, compressed under the 10 MiB request limit, and exceeding 64 MiB only in aggregate. It was run RED first: current behavior accepted the package. It now rejects it with the stable error code.
- Verification: `mvn '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest' test` passed 10 tests; the broader `mvn '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest,XlsxWorkbookWriterTest,AccountImportServiceTest,GradeImportServiceTest' test` passed 16 tests. Both runs had 0 failures/errors/skips. Mockito emitted existing dynamic-agent warnings in the import test run.
- Full suite after the change: `mvn test` passed 85 tests, 0 failures/errors, 43 skipped. All 21 Stage 3 and 22 Stage 4 MySQL cases skipped because no `MYSQL_TEST_*` values were configured; Redis and non-DB tests ran. Existing Mockito dynamic-agent warnings appeared.
- Updated `docs/API.md` and `docs/TESTING.md` with the 64 MiB package budget and error behavior. No MySQL/Redis integration, full suite, frontend build, or browser review was run in this update. Stage 4 remains partial pending isolated service and real-API visual acceptance.

## Local Administrator Recovery (2026-11-23)

- Added `recover-admin`, a local-profile-only command that requires exactly one active administrator, binds the temporary servlet context to loopback on an ephemeral port, validates the new password, increments `auth_version`, and records a redacted audit event. It refuses zero/multiple active administrators and never creates or selects an account ambiguously.
- The recovery password is passed through `ADMIN_RECOVERY_PASSWORD` for the one-shot process only. It is not written to `AGENTS.md`, README, source, logs, or another project file.
- `mvn -q -DskipTests package` exited 0. The one-shot recovery succeeded for the unique active administrator; database schema was already v5 and Flyway reported no pending migrations. Recovery process closed normally and the temporary password environment variable was cleared.
- Restarted the frontend dev server at port 5173 and started the local API at port 8080. `/actuator/health` and `/` both returned HTTP 200; no interactive browser login was performed, leaving credential entry for the user.
- Automated tests were not added or run in this update. Existing test results do not cover the new recovery command; Stage 4 remains partial.

## XLSX Boundary Regression Follow-up (2026-11-23)

- Added policy cases for a package within the aggregate expanded-byte budget, one ZIP member above the per-entry budget, and a forged ZIP local-header prefix with an invalid archive body. Existing coverage continues to exercise aggregate expansion above 64 MiB.
- Verification: `mvn '-Dtest=SpreadsheetUploadPolicyTest' test` passed 10 tests, 0 failures/errors/skips. No database, Redis outage/restart, frontend, or browser acceptance was run.
- AOCI Overview delivered the full 103-entry index, but its strict Challenge was 0/10; cognition remains uncertain and this continuation is source-bound. AOCI maintenance remains to be completed after this stable state.
- Remaining Stage 4 acceptance is unchanged: isolated Stage 4 MySQL rerun, shared Redis disconnect and full OS-process restart against isolated services, plus interactive real-API desktop browser review. Stage 4 remains partial.

## Stage 4 Acceptance Continuation (2026-11-23)

- Re-ran `mvn test`: 88 tests, 0 failures/errors, 43 skipped. The 21 Stage 3 and 22 Stage 4 MySQL cases skipped their schema guards; the remaining unit/Redis tests executed. This does not verify Stage 4 MySQL behavior in this run.
- Re-ran `npm run build`: success; output remains 535.88 kB JavaScript / 371.47 kB CSS, with Vite's >500 kB JavaScript warning.
- Confirmed `application-local.yml` targets database `stu_system`, not a disposable Stage 4 schema; `MYSQL_TEST_URL` is unset. No destructive integration run was attempted.
- `ego-browser skill` is available in this environment, but no authenticated isolated API test account or isolated Stage 4 database configuration was supplied. No browser flow was run. Shared Redis disconnect and full OS-process restart remain unverified.
- Next: provision session-only `MYSQL_TEST_URL` for a dedicated `stu_stage4_test` database and isolated Redis target; then run Stage 4 integration, outage/restart checks, and authenticated desktop browser workflows. Stage 4 remains partial.

## Stage 4 Authenticated Workbench Lazy Chunk (2026-11-23)

- Split the login entry from the authenticated workbench with a dynamic `DashboardView.vue` import. Element Plus components are now imported by the SFCs that use them instead of globally registered in `main.js`. Logout returns to the login screen, and username changes carry the new username into the login form.
- `npm run build` passed. The initial JS entry is 284.21 kB (104.97 kB gzip), and the authenticated workbench is a separate 264.23 kB chunk (86.51 kB gzip); CSS remains 371.47 kB (50.59 kB gzip). The initial JS entry is 251.67 kB smaller than the 535.88 kB prior entry and no >500 kB warning is emitted. Total emitted JS is 548.44 kB, 12.56 kB above the previous single chunk; this changes what unauthenticated users download, not total JS size.
- ego-browser snapshot and screenshot confirm the login page renders and the browser does not request the `DashboardView` chunk before authentication. Evidence: `docs/evidence/stage4/ego/login-lazy-bundle.png` at the browser's actual 1708x1078 viewport. No login was performed; account, grade, and personal-center workflows were not reaccepted.
- No JavaScript test script is configured. `git diff --check` produced no output. Isolated MySQL/Redis acceptance and real-API browser workflows remain open; Stage 4 remains partial.
- Next: verify the async workbench transition and logout/username re-login with an isolated API account; capture the actual authenticated workspaces at 1440x900 and 1280x800. Continue using only a dedicated `stu_stage4_test` schema.

## Stage 4 Final Work Continuation (2026-11-23)

- Read `AGENTS.md`, this task log, `REMAINING-WORK.md`, the Stage 4 handoff/roadmap, `TESTING.md`, `REQUIREMENTS.md`, and the development specification. Current AOCI rules and full 104-entry Overview were delivered; host delivery was confirmed, but the Challenge was 0/10, so cognition remains uncertain and work was verified against current source.
- Existing source/test evidence confirms `Stage4AccountSecurityMySqlTest` covers Spring application-context recreation and a separate-JVM Redis lock read. `AtomicRateLimitServiceRedisTest` covers refusal of a connection to localhost port 1. These do not establish shared Redis service disconnection or a full application OS-process restart.
- Current local runtime inspection found MySQL on 3306, Redis on 6379, API on 8080, and Vite on 5173. The API health endpoint and frontend root both returned HTTP 200. `application-local.yml` targets `stu_system`; `MYSQL_TEST_URL`, test credentials, and isolated Redis test variables are absent. No database or service state was changed.
- `mvn -q -DskipTests package` exited 0. `npm run build` exited 0: login JS 284.21 kB (104.97 kB gzip), authenticated workbench JS 264.23 kB (86.51 kB gzip), CSS 371.47 kB (50.59 kB gzip); no Vite size warning.
- ego-browser 2.2.1 opened the local login route and captured `docs/evidence/stage4/ego/final-login-2026-11-23.png`. Snapshot showed username, password, and login controls. No credential was entered; this is not authenticated API or workflow acceptance.
- No automated tests, MySQL integration tests, Redis interruption/restart, or authenticated API workflow were run. Stage 4 remains partial. Blockers: disposable `stu_stage4_test` configuration and session-only test account/API target for destructive integration and authenticated browser checks; an isolated Redis target for service-disconnect/process-restart evidence. Do not interrupt the current local/shared services.
- Documentation follow-up classifies evidence and open acceptance in `REMAINING-WORK.md` and corrects stale requirement-matrix statements. Production deployment remains out of scope.

## Stage 4 Acceptance Closure (2026-11-23)

- Fixed profile avatar loading: `avatarUrl` includes `/api/v1`, so passing it to an Axios instance based at `/api/v1` duplicated the prefix. The dashboard now requests `/me/avatar`; ego-browser confirmed image load, no profile error banner, and default-avatar restoration.
- Made the Vite API proxy target configurable through `STU_SYSTEM_API_PROXY_TARGET`, retaining `http://localhost:8080` as the default. This enabled separate local API/UI acceptance processes.
- Added configurable Redis test endpoints, a real isolated Redis process-disconnect test, a complete application OS-process restart test for revoked bearer tokens, Stage 4 profile audit success/failure and credential-redaction assertions, and a fixture test that generates XLSX browser inputs.
- Dedicated schema `stu_stage4_test_20261123codex`: Flyway validated and applied V1-V5, ending at version 5. Dedicated Redis ran on `127.0.0.1:16379`; the schema and temporary processes were removed after acceptance.
- Final `mvn -q test`: 92 tests, 0 failures/errors, 22 skipped. All 24 Stage 4 MySQL tests ran and passed. The 21 Stage 3 cases skipped their separate schema guard. The opt-in real Redis disconnect test was run separately and passed.
- `RedisServiceDisconnectAcceptanceTest` killed the isolated Redis process and verified fail-closed `503 RATE_LIMIT_UNAVAILABLE`. The application-process test verified a bearer token in the first process, revoked it, then confirmed a new process returned 401.
- Authenticated ego-browser 2.2.1 exercised account template/export/import, grade template/export/import, profile/username/contact, avatar upload/removal, current-password verification/change, and fresh login. The browser imported one synthetic teacher and one grade; both exports were downloaded then deleted. Stage 4 account/grade/profile pages were captured at 1440x900 and 1280x800 after data loading.
- Removed the repeated generic permission subtitle outside Overview, added accessible names to profile/password fields, and confirmed Tab focus is visible on the next action. Final screenshots: `docs/evidence/stage4/ego/final-copy-real-api-accounts-1440x900.png` and `final-copy-real-api-profile-1280x800.png`.
- The app builds passed: `mvn -q -DskipTests package` exit 0; final `npm run build` exit 0 with login JS 284.21 kB, workbench JS 264.34 kB, CSS 371.47 kB and no Vite chunk warning. `git diff --check` passed.
- Stage 4 is accepted for its clarified desktop-only scope. The full product is not complete: CAPTCHA and wider four-role/QA-22 requirements remain open.
