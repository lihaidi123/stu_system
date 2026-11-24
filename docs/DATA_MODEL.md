# Data Model

Logical model derived from the specification and checked against the current Flyway schema.

| Entity | Core identity and relationships | Invariants |
|---|---|---|
| Account | Stable account ID, business number, username, role, status, optional email/phone; optional student profile link | Normalized login identifiers resolve uniquely; non-null email/phone values are unique; password hash never leaves server; role/status/password changes increment `auth_version` |
| Student | Stable student ID, student number, name, college, major, class | Number is a string; cannot delete while grades exist |
| College | Stable ID and unique display name | Referenced colleges cannot be deleted |
| Class | Stable ID, grade, department, derived student count | Count includes current student profiles and is synchronized before commit on lifecycle writes |
| Course | Stable ID, name, college, default credits | Referenced courses cannot be deleted |
| Term | Stable ID, unique code, academic year, term order, display name | Grades reference stable ID; deletion restricted when referenced |
| Grade | Stable ID, student/course/term IDs, credit snapshot, assessment type, score, GPA | Unique student/course/term; decimal values; server-computed GPA |
| Teacher grants | Account-to-class and account-to-course relations | Unique pairs; effective scope is their intersection |
| Counselor grants | Account-to-class relation | Unique pair; no grant means empty scope |
| Derived summaries | Student and scope/term keyed totals and weighted metrics | Recomputed transactionally from grade facts |
| Class rankings | Student, class, term, exact weighted numerator and effective weight, rank | Compare ratios without rounding; ties use competition ranking; empty/zero-weight students have no projection row and are returned with null rank |
| Revocation and audit | Durable token revocation facts and append-only operation events | Fail closed for revocation; secrets excluded |
| Rate-limit state | Redis login and sensitive-password failure/lock keys | Atomic Lua increment; login aliases and sensitive checks are account-scoped; unavailable Redis fails closed |

All foreign keys use stable IDs. The current schema uses restrictive foreign keys for grade references, a unique account/profile binding, and a durable account `auth_version` increment when a student binding or account role/status/password changes. Login rate-limit state is stored in Redis rather than the `failed_login_count` and `locked_until` columns retained from Flyway V1; those legacy columns are currently unused. The account failure counter expires 15 minutes after its first failure, while reaching five failures starts a distinct 15-minute lock key.

## Implemented Migrations

Flyway V1 creates account, department, class, term, course, student profile, teacher course/class grants, grade, student-term summary, token revocation, and audit tables. V2 adds `class_term_ranking`; V3 preserves three fractional digits in effective weights, including half-credit assessment weights; V4 adds nullable unique account email and phone fields; V5 adds one owner-bound avatar reference per account and a private cleanup retry queue. Avatar object keys remain internal and are never returned by APIs. The ranking projection stores weighted numerator and denominator so comparisons do not round. IDs are BIGINT and API ID fields are serialized as strings. Grade score and credit values are DECIMAL; grade uniqueness is `(student_id, course_id, term_id)`. Grade and student lifecycle writes lock student IDs then class IDs in ascending order. GPA calculation uses a Spring-proxied calculator intercepted by `GradeGpaAspect`; summaries/rankings and census use `BEFORE_COMMIT` transactional listeners in `GradeDerivationAspect`. Student profile deletion is refused while grades or an account binding exists. A bound profile's student-number/name changes update the account business number/display name and increment `auth_version`. Audit rows retain redacted action metadata; no request body or credential material is stored.
