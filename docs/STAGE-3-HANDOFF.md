# Stage 3 Handoff

Prepared by: Stage 2
Handoff target: Stage 3, Grade and Consistency
Updated: 2026-11-20

## State

Stage 2 is handing off to the grade and consistency work defined in section 11.1 of [开发文档.md](开发文档.md). The repository already has a runnable identity and teaching-data slice. Grade creation, update, deletion, student-term summary refresh, and term-scoped class ranking projection are implemented; this does not mean Stage 3 acceptance is complete.

## Implemented

- Grade creation computes GPA and snapshots credits.
- Grade update/delete require an expected version. Teacher updates validate authorization for both the stored grade and requested destination.
- Summary and ranking projections refresh before commit for old and new student-term/class-term ownership.
- Ranking compares exact weighted numerator/denominator values, uses competition ties, and returns null average/rank for students without eligible grades.
- Ranking access is limited to administrators, assigned counselors, and students in the requested class. Teachers cannot view full-class rankings.
- `GET /api/v1/classes/{classId}/rankings/all-terms` returns separate term ranking rows ordered by academic year and term order. Focused ranking tests and Maven package passed; the endpoint SQL and MySQL authorization/transaction behavior remain unverified.
- `POST /api/v1/grades/batch` validates every row before writing and emits one deduplicated derived-data event after inserts; rollback and MySQL role/scope behavior remain unverified.
- Flyway V2 creates the ranking projection; V3 preserves three decimal places for half-credit assessment weights.

## Verification

- `mvn -q -Dtest=GradeRankingTest test`: passed for exact ratio ordering and competition ties.
- `mvn -q -DskipTests package`: passed.
- Flyway V2 and V3 applied to local MySQL 9.6 during application startup.
- Updated application started on port 8081; health returned `UP`; OpenAPI exposed grade update/delete and ranking routes.
- Authenticated end-to-end checks for update/delete, data-scope enforcement, transaction rollback, and concurrent ranking refresh remain unverified.

## Next Work

- Add MySQL integration coverage for rollback, concurrent updates within one class, old/new ownership changes, and role/object authorization.
- Verify grade batch and all-term ranking behavior against a disposable MySQL database, then cover student transfer/delete effects on summaries, rankings, and headcounts.
- Continue remaining identity, security, import/export, profile, audit, and frontend work listed in [TASKS.md](TASKS.md).
- Resolve the pending AOCI semantic update using the current tool contract; the previous two update payloads were rejected by argument validation and no formal index batch was committed.
