# Stage 4 Handoff

Updated: 2026-11-23

## Status

Stage 4 implementation and acceptance are complete within the user's desktop-only clarification. The final isolated MySQL/Redis failure-path checks, real-API browser flows, audit/redaction checks, and desktop screenshots were completed on 2026-11-23. See `Final Acceptance` below.

The user clarified on 2026-11-22 that this development does not include a mobile client. Stage 4 browser evidence is desktop-only at 1440x900 and 1280x800. This does not close the broader Stage 5 responsive acceptance requirements.

## Implemented

- Shared XLSX handling: 10 MiB per-file limit, 11 MiB request limit, OOXML parsing, POI decompression limits, 10,000 data-row cap, 64-column cap, formula/error-cell rejection, source-row and cell-type retention, bounded output and string-cell export.
- Account files: ADMIN-only template, row-atomic import, safe per-row report, duplicate identity skip without password replacement, existing unbound profile binding for STUDENT rows, filtered credential-free export.
- Grade files: ADMIN/TEACHER template, atomic import through `DomainService.addGradesBatch`, string student numbers and stable ID resolution, shared role-scoped grade query for export, no pagination truncation, explicit rejection over 10,000 rows.
- Profile and security: authenticated profile, username/contact updates and availability, email/phone normalization, account-scoped Redis sensitive-password limit (three failures lock for five minutes), password change repeats verification and increments `auth_version` in the same transaction.
- Avatar: JPEG/PNG/GIF/WebP validation and decode, 2 MiB and pixel bounds, UUID object names, local and COS adapters, owner-bound V5 metadata, cleanup retry queue, authenticated-owner read/remove routes.
- Desktop UI: account workbook workspace, grade workbook and filters, personal center, availability checks, password verification/change, and avatar controls using existing Element Plus components.

## Verification

- `mvn -q '-Dtest=Stage4AccountSecurityMySqlTest' test`: 22 passed, 0 failures/errors on `stu_stage4_test_20261122`. Flyway validated V1-V5 and applied V4/V5. Coverage includes account and grade files, 25-row unpaged grade export, teacher/student authorization, import rollback, profile/contact and credential changes, sensitive-password lock, avatar ownership/cleanup, OpenAPI operations, concurrent account identity creation, export bounds, and a separate-JVM Redis lock probe.
- `mvn -q test` against `stu_stage3_test_20261120`: 82 tests, 0 failures/errors, 22 Stage 4 MySQL cases skipped by the schema guard. The skipped cases were run separately on the Stage 4 schema.
- `mvn -q -DskipTests package`: passed.
- `npm run build`: passed. Vite reports a 1,129.63 kB main JavaScript chunk, above its 500 kB warning threshold.
- Isolated local smoke: `/actuator/health` returned `UP`; `/v3/api-docs` contained the account import, grade export, password verification, and avatar operations.
- ego-browser desktop captures are under `docs/evidence/stage4/ego/`; review notes are in `docs/evidence/stage4/README.md`. Authenticated screenshots use a browser-session-only static API fixture and are visual evidence, not backend verification.
- LeiBox searches found no suitable dense admin upload/table component; returned file-card/animation candidates were not adopted. 12ui CLI installation was attempted twice but stalled without output.
- AOCI maintenance batches were applied and the final maintenance response returned an aligned terminal proof. The subsequent full overview was delivered, but strict Challenge was partial (6/10); current full-system cognition must not be claimed reliable. A new session must follow the current AOCI rules and reload the current index before continuing.

## Acceptance History (Superseded on 2026-11-23)

- ZIP resource-abuse tests cover compression ratio, 256-entry count, 10 MiB per-entry expansion, and 64 MiB total expanded bytes. Aggregate expansion now fails before POI parsing.
- Verify a real shared Redis service disconnect and full OS-process restart against isolated services; the separate-JVM key visibility probe and Spring context restart test do not prove these conditions.
- Run an interactive ego-browser flow against the real local API, rather than the screenshot fixture.
- Consider dynamic import/code splitting to reduce the frontend main chunk, then rebuild and recheck desktop views.
- At the time this checklist was written, these acceptance items were open. They are closed by the 2026-11-23 evidence below. Production deployment remains out of scope.

## Frontend Optimization

This section records the requested frontend optimization scope. It follows [开发文档第 10.2 节](开发文档.md#102-apple-式简洁高效的具体要求); Stage 4 implementation remains desktop-first. The four-viewport and workflow review in section 10.4 still applies to the broader acceptance run.

User additions:

### 优化目标

- [x] Simplify Stage 4 account, grade, and personal-center pages by removing the repeated generic permission subtitle; retain necessary import safety guidance and workflow feedback.
- [x] Keep the existing Vue 3 / Element Plus patterns and avoid new dependencies; profile/password inputs now have explicit accessible names.
- [x] Evaluate and implement authenticated-workbench lazy loading; preserve account, grade, and personal-center flows with no Vite chunk warning.

### 优先页面 / 交互

- [x] Prioritize and verify ADMIN account import/export, grade query/import/export/manual entry, and personal-center profile/contact/password/avatar flows. Server scope remains authoritative.
- [ ] Review TEACHER, COUNSELOR, and STUDENT end-to-end workflows with isolated accounts as part of the broader four-role acceptance; Stage 4 evidence covers the ADMIN workspaces and backend scope tests.

### 验收标准

- [x] Match the Stage 4 section 10.2 baseline: restrained surfaces, readable hierarchy, no decorative effects, no repeated generic subtitles, and visible file/password safety guidance.
- [x] Tables and controls were reviewed at desktop sizes; profile fields expose accessible names and keyboard Tab focus is visibly indicated. Broader keyboard/reduced-motion review remains under QA-22.
- [x] `npm run build` succeeds; latest output is 284.21 kB login JS, 264.34 kB workbench JS, and 371.47 kB CSS with no chunk warning. Account, grade, and personal-center workflows were exercised against the real API.
- [x] ego-browser captures and rechecks use the real isolated API at 1440x900 and 1280x800. Evidence paths, role, workflow outcomes, and screenshot review are in `docs/evidence/stage4/README.md` and `docs/evidence/stage4/ego/`.
- [x] LeiBox searches and the decision not to adopt mismatched navigation animations are recorded in `docs/COMPONENT_SOURCES.md`.

### 补充记录

- 日期：2026-11-23
- 观察 / 决策：按用户要求将第 10.2 节作为样式与交互基线，重点优化三类现有工作区，并评估拆分 1,129.63 kB 主 chunk。用户要求 ego 仔细审核排版；只记录实际检查过的页面和视口。现代化以合适控件和清晰工作流为准，不为新颖性堆依赖。
- 变更与复核证据：本次仅补充验收范围，未改前端、未运行构建或 ego-browser。AOCI Overview 第 2 块续页因 cursor 参数错误失败，依仓库规则停止该认知链；后续源码任务须按新会话规则重建认知。TASKS 已记录本次文档变更与未执行验证。

Measured final build: login JS 284.21 kB, authenticated workbench JS 264.34 kB, and CSS 371.47 kB, with no Vite size warning. The mobile client is outside this Stage 4 scope.

The authenticated workbench split and real-API desktop review are complete for Stage 4. Continue broader four-role responsive and keyboard acceptance under the remaining product scope; fixture-based screenshots remain labeled visual-only.

## Final Acceptance (2026-11-23)

- Final isolated MySQL schema: `stu_stage4_test_20261123codex`; Flyway validated/applied V1-V5. Final `mvn -q test`: 92 total, 0 failures/errors, 22 skipped; all 24 Stage 4 MySQL cases passed. The isolated Redis outage test passed separately.
- The Stage 4 browser pass used a real isolated API, temporary Redis, and Vite proxy override. It exercised account/grade templates, imports and exports; profile, username/contact, avatar upload/remove and password re-login; success/failure audit coverage was verified by integration tests.
- Real API screenshots at 1440x900 and 1280x800 are in `docs/evidence/stage4/ego/final-real-api-*.png`; stable post-import and post-copy screenshots are `final-real-api-*-imported-*` and `final-copy-real-api-*`.
- Stage 4 is accepted within desktop-only scope. CAPTCHA, four-role workflow parity, and QA-22's broader four-viewport review remain outside this acceptance closure.

## Session Resume Checklist

1. Read `AGENTS.md`, `docs/TASKS.md`, and this handoff; then consult the relevant roadmap, requirement, test, API, and UI documents for the selected remaining item.
2. Follow the current AOCI session contract for this repository and reload/attest as required for a new run before making system-level assumptions.
3. Verify repository source and isolated local services before continuing. Do not use production or shared `stu_system` data for destructive fixtures.
4. Record only commands actually run and their results. Update `docs/TASKS.md` and this handoff when the final state changes; perform AOCI maintenance once after managed files reach their stable state.

## ZIP Resource-Abuse Update (2026-11-23)

- `SpreadsheetUploadPolicy` now configures Apache POI with a 256 ZIP-entry ceiling, in addition to the existing 1% inflate ratio and 10 MiB individual entry/text limits. `docs/API.md` records this contract.
- Generated XLSX cases verify rejection of a workbook with more than 256 entries and a highly compressible workbook part. Focused command: `mvn '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest,XlsxWorkbookWriterTest,AccountImportServiceTest,GradeImportServiceTest' test`; result: 15 passed, 0 failures/errors/skips.
- Full `mvn test` rerun passed 84 tests with 0 failures/errors and 43 skipped; the 21 Stage 3 and 22 Stage 4 MySQL cases were skipped because no `MYSQL_TEST_*` configuration was present. Redis and unit tests executed; existing Mockito dynamic-agent warnings appeared.
- The entry-count fixture was tightened to include 257 additional entries (above the 256 cap); final `mvn '-Dtest=SpreadsheetUploadPolicyTest' test` passed 6/6 with no failures/errors/skips.
- This does not demonstrate a total decompressed-byte budget across all ZIP entries. Shared Redis outage, full OS-process restart, and authenticated real-API browser evidence are still outstanding. In this session MySQL/Redis listeners existed, but an isolated Redis disconnect target was not established; ego-browser was not among the available tools, and no UI review is claimed.
- Stage 4 remains partial. Continue with isolated service setup and QA-17 restart/outage checks where isolation can be proven, then real-API browser review when ego-browser access and test credentials are available. Revisit the recorded frontend chunk-splitting optimization after the security/backend acceptance work.

## Frontend Bundle Update (2026-11-23)

- Replaced full Element Plus installation with explicit registration of the 17 components used by the current workbench. The existing stylesheet and component interactions are unchanged, and no dependency was added.
- Build output changed from 1,129.63 kB JS / 371.47 kB CSS to 535.88 kB JS (186.31 kB gzip) / 371.47 kB CSS (50.59 kB gzip). `npm run build` exited 0. The JS reduction is 593.75 kB (52.6%), but the 500 kB Vite warning remains by 35.88 kB.
- Tested Vite `manualChunks` for all Element Plus modules; it emitted a 1,034.30 kB Element Plus chunk and kept the warning, so the experiment was removed. Lazy route/workspace extraction remains a possible next optimization.
- LeiBox Vue recommendation for admin tables/navigation/upload feedback returned navigation animations that did not fit this workbench; none was adopted. See `docs/COMPONENT_SOURCES.md`.
- No ego-browser visual review or real-API flow was run in this update. Do not claim the screens were visually reaccepted. Stage 4 remains partial pending that review and isolated-service backend evidence.

## Combined XLSX Expansion Budget (2026-11-23)

- The upload policy now streams ZIP members with an 8 KiB buffer before POI parsing. It rejects any member above 10 MiB or cumulative actual expanded bytes above 64 MiB with `FILE_EXPANDED_TOO_LARGE`; the reader applies this before parsing both account and grade workbooks.
- A valid package fixture contains eight 8.5 MiB expanded members, while the compressed workbook remains below the 10 MiB upload limit and each member stays below the per-entry limit. The fixture failed against prior behavior and passes with the aggregate guard.
- `mvn '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest' test`: 10 passed. `mvn '-Dtest=SpreadsheetUploadPolicyTest,XlsxWorkbookReaderTest,XlsxWorkbookWriterTest,AccountImportServiceTest,GradeImportServiceTest' test`: 16 passed; 0 failures/errors/skips. Existing Mockito dynamic-agent warnings appeared in the broader focused run.
- `docs/API.md` and `docs/TESTING.md` now describe the aggregate limit and `FILE_EXPANDED_TOO_LARGE`. Shared Redis outage, app OS-process restart, isolated Stage 4 MySQL integration, and real-API ego-browser acceptance remain outstanding; Stage 4 stays partial.
- Full `mvn test` after the policy change passed 85 tests with 0 failures/errors and 43 skipped. The Stage 3 and Stage 4 MySQL classes skipped by their schema guards because no `MYSQL_TEST_*` configuration was present; the Redis and other non-DB cases executed.

## Continue

1. Read `AGENTS.md`, `docs/TASKS.md`, and this handoff.
2. Establish isolated Stage 4 MySQL/Redis configuration and session-only admin credentials; never use shared `stu_system` for destructive fixtures.
3. Run the Stage 4 MySQL suite and verify the shared-service outage/process-restart cases only against isolated targets.
4. Review the real API desktop workflows with ego-browser at 1440x900 and 1280x800 when the browser tool and isolated services are available. ZIP ratio and entry-count fixtures are already covered by the recorded unit runs.
5. Consider lazy workspace extraction for the remaining 535.88 kB JS entry, then rebuild and capture actual browser evidence before updating acceptance status.

The local Vite development server for this session is at `http://127.0.0.1:5173/`.

## XLSX Boundary Regression Follow-up (2026-11-23)

- Added tests for acceptance within the combined expansion budget, rejection of a single expanded ZIP member above 10 MiB, and rejection of a forged ZIP local-header signature with an invalid body.
- Verification: `mvn '-Dtest=SpreadsheetUploadPolicyTest' test` passed 10/10.
- No isolated MySQL/Redis outage or process-restart verification and no real-API browser review were run in this continuation. Stage 4 remains partial; see the remaining acceptance items above.

## Acceptance Continuation (2026-11-23)

- Current `mvn test`: 88 tests, 0 failures/errors, 43 skipped. The 21 Stage 3 and 22 Stage 4 MySQL tests skipped because no matching test URL was configured; unit and Redis tests executed.
- Current `npm run build`: success, 535.88 kB JavaScript and 371.47 kB CSS; the JavaScript chunk warning remains.
- The local `application-local.yml` targets `stu_system`, and `MYSQL_TEST_URL` is unset. Do not use the local profile for destructive integration tests; provide a dedicated `stu_stage4_test` schema.
- ego-browser CLI is installed (`ego-browser skill` returned guide v2.2.1), but this continuation did not run a browser flow because no isolated Stage 4 API/database and test account were configured. Shared Redis outage and OS-process restart also remain unverified.
- Next: configure dedicated Stage 4 MySQL and Redis targets plus session-only test credentials, then run the Stage 4 MySQL class, QA-17 outage/restart scenarios, and real-API desktop flows at 1440x900 and 1280x800. Keep Stage 4 partial until those results are recorded.
- The earlier follow-up to consider lazy workspace extraction is now addressed by the authenticated workbench async split above; its remaining acceptance is to verify the post-login workflows and required desktop viewports.

## Authenticated Workbench Lazy Chunk (2026-11-23)

- The login screen now dynamically loads `DashboardView.vue` only after a successful login or when a stored token is present. Element Plus components are imported by the SFCs that use them; `main.js` no longer globally imports and registers the full workbench component set. Logout returns to login, and username-change logout preserves the new identifier in the form.
- Build: `npm run build` passed. Initial JS entry: 284.21 kB (104.97 kB gzip); authenticated workbench chunk: 264.23 kB (86.51 kB gzip); CSS: 371.47 kB (50.59 kB gzip). The initial JS entry fell 251.67 kB (47.0%) from the 535.88 kB single entry, and Vite emitted no size warning. Total emitted JS is 548.44 kB, 12.56 kB higher than before, so the change defers workbench code for unauthenticated users but does not reduce total JS.
- ego-browser showed the login page and verified no `DashboardView` resource was requested before authentication. Screenshot: `docs/evidence/stage4/ego/login-lazy-bundle.png`, actual viewport 1708x1078. This is a login-page smoke only; no authenticated role workflow was exercised.
- Remaining: verify successful login, async workbench loading, logout and username-change relogin using an isolated test account; capture authenticated account, grade, and profile pages at 1440x900 and 1280x800. MySQL destructive tests must use a `stu_stage4_test` schema, never the local `stu_system` profile. Stage 4 remains partial.

## Current Status (2026-11-23)

Stage 4 acceptance is complete for the clarified desktop-only scope. Use the Final Acceptance section above as the current result; later-dated blocks below it are historical continuations whose statuses are superseded. The remaining product scope is tracked in `docs/REMAINING-WORK.md`: CAPTCHA, complete four-role requirement parity, and broader QA-22 four-viewport/keyboard acceptance remain open. The user requested overall full completion; continue with the approved scope once its design gate is satisfied. Production deployment remains excluded.
