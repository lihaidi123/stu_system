# Requirements Matrix

Source of truth: [开发文档.md](开发文档.md). This initial matrix groups the complete specification; individual IDs are tracked here as implementation proceeds.

| Area | IDs | Required behavior | Status |
|---|---|---|---|
| Authentication | AUTH-01..06 | Unified login, one-time captcha, throttling, revocation, current identity, immediate credential invalidation | Partial: Redis login/sensitive-password limits, revocation, role/version checks, context recreation, separate-JVM visibility, real Redis process outage, and full application-process restart are verified; CAPTCHA lifecycle and broader identity acceptance remain open |
| Profile and system | PRO-01..07, SYS-01..02 | Profile, credential and contact changes, avatar, stable errors and UI states | Stage 4 acceptance complete: isolated profile/contact/username/password/avatar integration, real-API desktop flow, avatar failure handling, and stable session re-login verified; broader role-specific states remain open |
| Administration | ADM-01..15 | Accounts, organization, courses, terms, student records, grants, grades, imports/exports, statistics | Partial: account and grade workbook imports/exports and role scopes passed isolated Stage 4 integration and real-API browser workflows; broader administration and all-role acceptance remain open |
| Cross-cutting | AOP-01..04 | GPA, derived summaries/rankings, class size, audit | GPA, derived summaries/rankings and class size are verified; Stage 4 audit success/failure and password/token redaction now have integration evidence. Broader operation audit coverage remains open |
| Grade rules | Sections 4.1..4.6 | Decimal validation, GPA, weights, summaries, rankings, dashboards | Grade CRUD/batch, term/all-term rankings, role-scoped dashboard, atomic workbook import and filtered export are verified by unit, isolated MySQL and real-API browser evidence; remaining role parity is tracked below |
| Security | Sections 7 and 8 | Token lifecycle, blacklist, data scope, upload safety | Partial: current-role/version rejection, MySQL failure, Redis fail-closed behavior during real process outage, application OS-process restart, scope tests, account changes and XLSX resource bounds are verified; CAPTCHA and broader lifecycle acceptance remain open |
| Frontend | Section 10 | Four role workflows, responsive UI and actual browser review | Stage 4 desktop workflows passed real-API ego-browser review at 1440x900 and 1280x800. Four-role and four-viewport QA-22 review (768x1024 and 390x844 included) remains open |
| Delivery | Sections 2, 11..13 | Local setup, docs, tests, initialization, independent AOCI | Stage 4 local acceptance and AOCI are aligned; the broader product remains partial pending CAPTCHA, four-role workflows, and remaining requirement IDs |

## Scope Guard

Production deployment, server setup, Tomcat, WAR packaging, domains, reverse proxies, and launch work are excluded by the source specification.
