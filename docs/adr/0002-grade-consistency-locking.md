# ADR 0002: Grade and Student Consistency Locking

Date: 2026-11-20
Status: Accepted for the Stage 3 local implementation

## Context

Grade changes rebuild student-term summaries and whole class-term ranking projections. Student transfers and deletion also change class census and rankings. Locking only the class after reading a student's class permits a transfer to race with a grade write that captured the old class.

## Decision

- Grade and student lifecycle transactions lock affected `student_profile` rows first, in ascending student ID order.
- After reading the class IDs protected by those student locks, transactions lock affected `academic_class` rows in ascending class ID order.
- Grade facts, summary/ranking projections, student class membership, and class census update in the same transaction. Grade derivation remains a `BEFORE_COMMIT` transactional event.
- GPA is calculated through the Spring proxy for `GradeGpaCalculator`; `GradeGpaAspect` validates the score and returns the server-derived point value. Student create/batch/transfer/delete and explicit rebuild publish census-change events; `GradeDerivationAspect` recounts locked classes at `BEFORE_COMMIT`.
- Batch writes validate all rows before insertion, then acquire the same ordered locks and emit one event with deduplicated affected scopes.
- Audit advice wraps transactional business methods. Success audit is recorded after the inner transaction commits; failure audit uses a separate transaction after rollback. Audit write failure is logged without secrets and does not replace the business result/error.

## Consequences

Concurrent writes to different students in one class serialize at the class row before recomputing rankings. A transfer and grade mutation of the same student serialize at the student row; the later transaction reads the committed class membership. Multi-student operations acquire rows in stable ascending order to reduce deadlock risk. The approach favors consistency over write throughput for the same class.

## Verification

`mvn -q test` passed 28 tests with all 21 `GradeConsistencyMySqlTest` cases running against dedicated schema `stu_stage3_test_20261120`. Authenticated HTTP scopes, lifecycle routes, GPA proxy, census listener, same-class concurrency, transfer, and ranking-trigger rollback/failure audit passed. Flyway 11.14.1 warned MySQL 9.6 is newer than its verified version (8.1); all three migrations validated and version 3 was current.
