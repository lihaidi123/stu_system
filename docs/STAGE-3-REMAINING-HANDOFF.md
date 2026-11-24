# Stage 3 Remaining Work Handoff

Updated: 2026-11-21

Stage 3 (Grade and Consistency), section 11.1 item 3 of [开发文档.md](开发文档.md), is complete for its defined scope. User and Excel import subcases of QA-15 remain deferred to later import work. Read [AGENTS.md](../AGENTS.md), [TASKS.md](TASKS.md), and current AOCI rules/state when starting subsequent work.

## Scope Boundary

Stage 3 covers grade rules and writes, GPA, student summaries, class rankings, student lifecycle/headcount consistency, transactional/concurrency behavior, and audit responsibility. CAPTCHA/rate limiting, broader account security, personal center, Excel/COS, full frontend, LeiBox and visual review are later work.

## Current Implementation

- Grade create/batch/update/delete use server GPA, stable student/course/term IDs, credit snapshots, optimistic versions, atomic validation and transactional derived updates.
- `GradeGpaCalculator` is a proxied Spring service intercepted by `GradeGpaAspect`, which validates decimal score bounds and calculates GPA.
- `GradeDerivationAspect` consumes grade-change and student-census events at `BEFORE_COMMIT`; affected class/student rows are locked in ascending order before event publication.
- Student lifecycle operations maintain old/new class census and rankings, protect bound/graded profiles from deletion, and invalidate linked credentials when identity changes.
- Role-scoped grade/student/detail/statistics/ranking routes, `/me/grades` pagination, class statistics, explicit rebuild, and redacted audit query exist.
- Authenticated MockMvc and isolated MySQL cases have been added for scopes, route precedence, page totals/limits, lifecycle endpoints, stale versions, batch row errors, multi-term ranking SQL, GPA proxy, census, concurrency, rollback and zero-credit behavior.

## Verification

- `mvn -q clean`: exit 0.
- `mvn -q test`: exit 0; 28 tests passed, 0 failures/errors/skips, including all 21 `GradeConsistencyMySqlTest` cases on `stu_stage3_test_20261120`.
- `mvn -q -DskipTests package`: exit 0 after the latest source and test changes.
- `GradeGpaAspectTest`: two passing tests cover documented GPA thresholds, invalid ranges/precision and Spring proxy activation.
- Java 23.0.1 and Maven 3.9.13 are available.
- Local app smoke used the `test` profile against the same isolated schema on port 8181: `/actuator/health` returned `UP`; `/v3/api-docs` returned OpenAPI 3.1.0 and contained the student-grade and administrator routes.
- Flyway 11.14.1 warned MySQL 9.6 is newer than its verified version 8.1; all three migrations validated and schema version 3 was current.

## Test Setup

### Dedicated Test Database

The local MySQL test schema is isolated from the application database:

- Host: `127.0.0.1`
- Port: `3306`
- Schema: `stu_stage3_test_20261120`
- Username: `root`
- JDBC URL: `jdbc:mysql://127.0.0.1:3306/stu_stage3_test_20261120?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`
Do not store test credentials in this document or project files. Prompt for the password as a secure string in the local shell, then populate process-only `MYSQL_TEST_*` variables.

Set `MYSQL_TEST_URL`, `MYSQL_TEST_USERNAME`, and `MYSQL_TEST_PASSWORD` in the current shell. `GradeConsistencyMySqlTest` refuses URLs whose schema does not start with `stu_stage3_test`; its fixture setup deletes rows only in that isolated schema. Never point it at `stu_system`.

PowerShell invocation pattern:

```powershell
$secure = Read-Host -AsSecureString 'MYSQL_TEST_PASSWORD'
$env:MYSQL_TEST_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
$env:MYSQL_TEST_URL = 'jdbc:mysql://127.0.0.1:3306/stu_stage3_test_20261120?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:MYSQL_TEST_USERNAME = 'root'
mvn -q test
$testExit = $LASTEXITCODE
Remove-Item Env:MYSQL_TEST_PASSWORD,Env:MYSQL_TEST_URL,Env:MYSQL_TEST_USERNAME -ErrorAction SilentlyContinue
exit $testExit
```

### Deferred Work

QA-15 user import and Excel import remain later file/import work; no import coverage is claimed. CAPTCHA/rate limiting, broader account lifecycle, personal center, Excel/COS, full frontend, LeiBox review and ego-browser acceptance also remain later phases.

Do not claim the whole product is complete; later phases remain outside Stage 3.
