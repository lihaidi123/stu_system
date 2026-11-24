# API Contract

The planned implementation follows the `/api/v1` contract in sections 8.1–8.2 of [开发文档.md](开发文档.md). Response envelopes, pagination, date/decimal serialization, error codes, and endpoint details must be transcribed and kept synchronized here before endpoint implementation.

## Security Contract

The required server contract independently authenticates every protected request and authorizes the target object and requested fields. Client menus and route guards are usability features only. Teachers are scoped to assigned class/course intersection; counselors to assigned classes; students to their linked profile. Empty grants produce empty result sets.

## File Contract

The shared spreadsheet boundary accepts only `.xlsx` with the XLSX MIME type, rejects empty files and uploads above 10 MiB, validates the OOXML workbook with Apache POI, and caps ZIP packages at 256 entries, 64 MiB combined actual expanded bytes, a 1% inflate ratio, and 10 MiB per expanded entry/text. Exceeding the combined or per-entry expanded-byte budget returns HTTP 400 with `FILE_EXPANDED_TOO_LARGE`. Output text is capped at 8 MiB, output cells at 100,000, and data rows at 10,000. Spring multipart limits are 10 MiB per file and 11 MiB per request; framework-level size rejection returns HTTP 413 with `FILE_TOO_LARGE`. Parsing accepts one sheet and 64 columns, rejects formula/error cells, preserves source row numbers and cell types, and closes workbook/stream resources. Export writes all user-provided values as string cells.

## Status

OpenAPI is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## Implemented initial endpoints

- `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/me`
- `GET/POST /api/v1/admin/{departments|classes|courses|terms|students|accounts}` (administrator only)
- `PATCH/DELETE /api/v1/admin/students/{id}`, `POST /api/v1/admin/students/batch`, and `PUT /api/v1/admin/students/{id}/account` support profile edits/transfers, atomic creation, reference-protected deletion, and explicit account link/unlink (ADMIN). Student number/name changes update the linked student's business number/display name and increment `auth_version`.
- `GET /api/v1/admin/audit-events?limit=100` returns redacted audit metadata (ADMIN, limit clamped to 1–500).
- `POST /api/v1/grades/batch` accepts 1–1000 grade objects. Every row is validated and authorized before insertion; duplicate/conflicting rows or any invalid row fail the whole transaction. GPA, credit snapshots, summaries and rankings use the same server rules as single-grade creation.
- `GET /api/v1/grades/import-template` and `POST /api/v1/grades/import` are ADMIN/TEACHER-only. Import resolves text student number, course name, and term code to stable IDs, validates the full workbook, and calls the transactional batch grade path once; any invalid or unauthorized row leaves no grade or derived rows. The report contains only row numbers and aggregate counts.
- `GET /api/v1/grades/export` is ADMIN/TEACHER-only and accepts the same student, term, class, course, score-range, and keyword filters as grade listing. It selects the complete filtered scope without page parameters. More than 10,000 matches returns `EXPORT_TOO_MANY_ROWS` rather than truncating.
- `GET /api/v1/admin/accounts`, `PUT /api/v1/admin/accounts/{id}/{course|class}/{targetId}`
- `GET /api/v1/admin/accounts/import-template` (ADMIN) returns an XLSX template with the seven documented identity/contact/role/status columns. It contains headers only and no credentials or sample account data.
- `POST /api/v1/admin/accounts/import` (ADMIN, multipart `file` and separate `temporaryPassword`) commits each valid row through the existing account transaction path. Existing identifiers are skipped, invalid rows are reported by source row, student accounts must bind an existing unbound profile, and the temporary password is never returned. Contact fields are normalized and uniquely constrained. Reports contain only source row, stable result code, and fixed safe message.
- `GET /api/v1/admin/accounts/export?keyword=&role=&status=` (ADMIN) exports all filtered account rows independent of pagination and excludes credential fields. Results above 10,000 rows are rejected with `EXPORT_TOO_MANY_ROWS`, never silently truncated.
- `GET /api/v1/me/profile`, `PATCH /api/v1/me/username`, `PATCH /api/v1/me/contact`, and `GET /api/v1/me/availability?field=&value=` operate only on the authenticated account. Contact fields normalize to lowercase email and an 11-digit mainland-China phone; explicit null clears nullable values. Username changes increment `auth_version` and require login again.
- `POST /api/v1/me/password-verifications` verifies the current password without issuing a reusable verification flag. Three consecutive account-scoped failures lock verification for five minutes; Redis errors fail closed. `PUT /api/v1/me/password` applies the same check and changes password plus `auth_version` in one transaction.
- `POST /api/v1/me/avatar`, `GET /api/v1/me/avatar`, and `DELETE /api/v1/me/avatar` replace, read, and remove only the authenticated owner's avatar. The response exposes only the application-owned route, never a COS key. Upload validates declared and decoded content type, size and dimensions. Replacements stage cleanup work before upload, then commit metadata before cleaning the old object; failed cleanup is retried asynchronously.
- `PUT /api/v1/admin/accounts/{id}/role`, `PUT /api/v1/admin/accounts/{id}/status`, and `PUT /api/v1/admin/accounts/{id}/password` (ADMIN) update role, explicitly set `ACTIVE`/`DISABLED`, or reset a password. Actual changes increment `auth_version`; role changes remove stale grants, bound student accounts must be explicitly unbound first, and the final active administrator cannot be disabled or demoted.
- `GET /api/v1/scope/options`, `GET /api/v1/students`, `GET/POST /api/v1/grades`; grade listing accepts term, student, class, course, score range, and keyword filters, all applied with server-side role scope.
- `PATCH /api/v1/grades/{id}` accepts the expected `version` and editable student/course/term/credits/assessment/score fields. The server checks the existing record and requested destination independently; teachers need the class/course grant intersection for both.
- `DELETE /api/v1/grades/{id}?version={version}` rejects stale versions and recalculates affected summaries and rankings transactionally.
- `GET /api/v1/classes/{classId}/rankings?termId={termId}` exposes term rankings to administrators, assigned counselors, and students in that class. Teachers cannot read full-class rankings. Students without a weighted grade appear with a null average and rank.
- `GET /api/v1/classes/{classId}/rankings/all-terms` exposes each term as an independent ranking row in academic year/term order. It does not combine scores across terms; authorization matches the single-term route.
- `GET /api/v1/me/grades?termId=&page=1&pageSize=20` returns the authenticated student's records with a scoped `items/total/page/pageSize` envelope. Page starts at 1; page size must be 1–100. `GET /api/v1/me/grade-summary` and `GET /api/v1/students/{id}/grade-summary` return visible records and summaries; student callers are always scoped to their linked profile.
- `GET /api/v1/admin/classes/statistics`, `POST /api/v1/admin/derived-data/rebuild` are administrator-only census/statistics and consistency-repair routes. `GET /api/v1/admin/audit-events` is also administrator-only.
- `GET /api/v1/analytics/overview?termId=&classId=&courseId=` returns role-scoped dashboard metrics, score bands, course averages and term trends.
- `GET /api/v1/admin/audit-events?limit=` lists audit metadata without passwords, tokens, request bodies or secrets.

The current API remains short of full section 8 acceptance. Authentication uses one-hour signed bearer credentials, durable MySQL revocation on logout, and `auth_version` checks. Invalid or expired credentials return HTTP 401 with stable `UNAUTHORIZED`; inability to check MySQL credential state returns HTTP 503. Login failures use Redis atomic counters: the 15-minute counter window begins on the first failure and the fifth failure starts a separate 15-minute lock; Redis failure returns 503. Role, status, username, password and administrator reset changes invalidate prior credentials, and bearer authorization compares the token role to the current database role. Captcha and avatar operations remain unimplemented. Account and grade workbook endpoints plus profile/password verification source paths exist; Stage 4 acceptance still requires isolated database tests, storage failure tests, frontend work, and visual evidence.
