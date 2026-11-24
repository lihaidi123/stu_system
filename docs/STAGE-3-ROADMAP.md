# Stage 3 Acceptance Record

Updated: 2026-11-21

Stage 3 (Grade and Consistency), section 11.1 item 3 of [开发文档.md](开发文档.md), is complete for its defined scope. User and Excel import subcases of QA-15 are deferred to later file/import work.

## Delivered

- Single, batch, update and delete grade paths compute server GPA, preserve credit snapshots, enforce optimistic versions and update summaries/rankings transactionally.
- `GradeGpaAspect` intercepts the proxied GPA calculator. `GradeDerivationAspect` applies student summaries, rankings and student census changes before transaction commit.
- Student create/batch/edit/transfer/delete and explicit account binding use ordered student/class locks and preserve old/new class ranking and census consistency.
- Teacher access uses the intersection of granted classes and courses; counselor and student reads remain server-scoped. Counselor/student grade writes are rejected.
- `/api/v1/me/grades` supports database pagination with totals and page-size validation. Term and all-term ranking routes keep term scopes separate.
- Audit success/failure paths, class statistics, explicit derived-data rebuild, and redacted audit reads are covered.

## Acceptance Evidence

- `mvn -q clean`: passed.
- `mvn -q test`: passed 28 tests, 0 failures, 0 errors, 0 skips. The 21 `GradeConsistencyMySqlTest` cases ran against dedicated schema `stu_stage3_test_20261120`.
- `mvn -q -DskipTests package`: passed.
- Local app smoke ran with the `test` profile against that isolated schema on port 8181. `/actuator/health` returned `UP`; `/v3/api-docs` returned OpenAPI 3.1.0 and contained `/api/v1/me/grades`, `/api/v1/admin/students`, `/api/v1/admin/classes/statistics`, `/api/v1/admin/audit-events`, and `/api/v1/admin/derived-data/rebuild` with expected methods.
- Java 23.0.1 and Maven 3.9.13 were used. Flyway 11.14.1 validated migrations V1–V3 and reported schema version 3 current. It warned that MySQL 9.6 is newer than its verified version 8.1.

## QA Coverage

- QA-07: GPA threshold boundaries, precision and out-of-range input covered by `GradeGpaAspectTest`.
- QA-08: documented summary example, half-weight metrics, exact weighted ranking, tie handling and term partitions covered by unit and MySQL tests.
- QA-09: grade create/update/move/delete, atomic batch validation, and derived summaries/rankings covered by MySQL tests.
- QA-10: student create/batch/transfer/delete census synchronization and reference protection covered by MySQL and authenticated HTTP tests.
- QA-11: injected ranking failure verifies transaction rollback and separate failure audit; duplicate and stale-version conflicts are covered.
- QA-12: empty results, zero score, zero effective weight and null averages are covered.
- QA-13: duplicate concurrent creates and stale-version conflicts are covered.
- QA-14: same-class concurrent writes verify summaries and ranking projections after commit.
- QA-15: user import and Excel import are deferred; no coverage is claimed for those subcases.

## Outside Stage 3

CAPTCHA/rate limiting, broader account lifecycle, personal center, Excel/COS features, complete frontend workflows, LeiBox review and ego-browser visual acceptance remain later phases. Production deployment is excluded.
