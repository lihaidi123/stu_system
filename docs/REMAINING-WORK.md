# Remaining Work Summary

Updated: 2026-11-23

This document summarizes open product work after Stage 4 acceptance. Status reflects the final isolated tests, real-API browser review, build, and AOCI maintenance performed on 2026-11-23.

## Stage 4: Accepted

Stage 4 acceptance is complete for the clarified desktop-only scope. It covers account/grade workbook workflows, profile/password/contact, avatar lifecycle, security failure paths, audit redaction, and the desktop workbench.

- [x] Complete `Stage4AccountSecurityMySqlTest` and validate V1-V5 on disposable schema `stu_stage4_test_20261123codex`.
- [x] Verify a real Redis process outage on temporary Redis port 16379 and application-process restart after token revocation.
- [x] Exercise authenticated account and grade imports/exports, profile/username/contact, avatar, password verification/change and new-login flow against the real local API.
- [x] Verify Stage 4 audit success/failure metadata and password/token redaction.
- [x] Reconcile test, build and browser evidence in the task, testing, requirements, handoff and evidence records.

### Final Acceptance Evidence (2026-11-23)

- Final `mvn -q test`: 92 tests, 0 failures/errors, 22 skipped. All 24 Stage 4 MySQL tests passed; 21 Stage 3 cases skipped their separate schema guard. The opt-in Redis process-disconnect acceptance passed separately.
- Flyway validated/applied V1-V5 to the disposable Stage 4 schema. Temporary schema, Redis, API and UI processes were removed; shared services and `stu_system` were left untouched.
- `mvn -q -DskipTests package` and final `npm run build` passed. Login JS is 284.21 kB, workbench JS 264.34 kB, CSS 371.47 kB; no Vite chunk warning.
- Real-API ego-browser evidence is in `docs/evidence/stage4/ego/final-real-api-*.png` at 1440x900 and 1280x800. Account and grade imports each created one synthetic record; template/filtered exports downloaded successfully and were deleted after verification.
- Profile username/contact, avatar upload/removal, current-password verification/change, fresh login, password/token redaction, Redis outage failure-closed behavior, and revoked-token rejection after a complete app process restart passed.
- Fixed the duplicate `/api/v1` avatar URL composition and verified image load/removal without an error banner.
- Removed the repeated generic permission subtitle outside Overview, added accessible names to profile/password inputs, and verified Tab focus is visible. Final screenshots are `final-copy-real-api-accounts-1440x900.png` and `final-copy-real-api-profile-1280x800.png`.

## Remaining Product Scope

The user's request to complete all remaining work authorizes the broader product scope. Stage 4 closure does not complete these requirements:

- [ ] Implement and verify the one-time CAPTCHA lifecycle and its interaction with login throttling, expiry, reuse rejection, and Redis failure behavior.
- [ ] Complete ADMIN, TEACHER, COUNSELOR, and STUDENT workflows against the documented route and data-scope matrix, including role-specific options, records, summaries, filters, and errors.
- [ ] Complete QA-22 keyboard and visual review at 1440x900, 1280x800, 768x1024, and 390x844; retain per-role evidence and fix findings.
- [ ] Review remaining `AUTH`, `ADM`, `PRO`, `SYS`, `AOP`, `QA`, and data-scope requirements individually; update `REQUIREMENTS.md` only with concrete executed evidence.
- [ ] Add a staged roadmap and a reviewed design for these independent authentication, administration, and UI workstreams before implementation.

## Local Setup and Operational Follow-up

- [ ] Keep destructive MySQL integration runs isolated to their required `stu_stage3_test` or `stu_stage4_test` schema prefixes; ensure required test credentials/services are configured without recording secrets in repository files.
- [ ] Preserve the local-only administrator recovery boundary and verify any future recovery changes with their own tests; the 2026-11-23 recovery command was packaged and exercised, but no automated tests were added for it in that update.
- [ ] Treat local servers and credentials as developer environment state. Production deployment and production server work are explicitly out of scope.

## Next Sequence

1. Design the remaining work as separately testable CAPTCHA/security, role workflow, and QA-22 acceptance stages.
2. Implement and verify requirements in source order, preserving the server-side role/data-scope and credential safety invariants.
3. Keep production deployment out of scope.

## Blockers and Boundaries

- No Stage 4 environment blocker remains. Its temporary schema and services were removed after verification.
- Broader product requirements remain unimplemented or unverified; their scope is recorded above. Do not mark them complete based on Stage 4 evidence.
- No production deployment work is included.
