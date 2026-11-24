# Stage 4 Evidence

## Desktop UI

ego-browser 2.2.1 captured the actual Vue app shell at 1440x900 and 1280x800. The login and ADMIN account, grade, and personal-center screens are under `ego/`.

After splitting the authenticated workbench into an async chunk, ego-browser 2.2.1 confirmed the login screen still renders at 1708x1078 and that the browser does not request `DashboardView` before authentication. See `ego/login-lazy-bundle.png`. This smoke did not authenticate and does not verify the workbench transition.

The authenticated screenshots use a browser-session-only static API fixture. They verify layout and visible interactions only; they do not call or validate the backend. Mobile-specific review is out of scope per the user's 2026-11-22 clarification.

## Review Notes

- Account screen: account-file actions, role/status filters, temporary password field, upload feedback, and credential-free account table fit the desktop content width. Added contact columns and made them stable even when the first account has no contact values.
- Grade screen: template/export actions, keyword/term/course/class/score filters, import state, manual entry, and grade table remain visible without overlap at both desktop viewports. Filters wrap to a second row at 1280x800.
- Personal center: profile fields, contact availability checks, avatar controls, and password verification/change remain visible at 1280x800.
- Login screen: title and credential form remain distinct and fit at 1440x900.
- Fixes after review: read paginated student options from `items`; load profile when its navigation item is selected; retain the renamed username in the login field after credential invalidation; do not clear unrelated localStorage keys on logout; keep contact table columns stable.
- No backend state, actual uploaded workbook, avatar bytes, or real credential was used in the screenshot fixture.

An isolated backend smoke was also run against `stu_stage4_test_20261122`: `/actuator/health` returned `UP`, and `/v3/api-docs` contained the account import, grade export, profile password, and avatar routes. This smoke did not sign in or exercise those routes; the authenticated screenshots above remain fixture-based.

## Known Gaps

- Stage 4 real-API desktop acceptance is closed. The older static-fixture screenshots above remain historical visual-only evidence.
- No mobile-specific work or screenshots were performed, per scope clarification. QA-22's broader four-role review at 768x1024 and 390x844 remains a later product requirement.
- CAPTCHA lifecycle, complete four-role API/UI parity, and broader administration requirement coverage remain open outside Stage 4.

## Real API Acceptance (2026-11-23)

- ego-browser 2.2.1 ran against an isolated API on 8189, disposable schema `stu_stage4_test_20261123codex`, isolated Redis on 16379, and Vite on 5175. Shared API/MySQL data and shared Redis were not used for destructive fixtures.
- Authenticated ADMIN account, grade, and personal-center workspaces were captured after their data loaded at 1440x900 and 1280x800. Stable screenshots are `ego/final-real-api-accounts-<viewport>.png`, `ego/final-real-api-grades-<viewport>.png`, and `ego/final-real-api-profile-<viewport>.png`.
- The browser downloaded account and grade templates and exports; the temporary downloads were deleted. It imported one synthetic teacher and one grade row, verified row success/grade display, changed username and contacts, uploaded and removed an avatar, verified the current password, changed it, and authenticated again with the new credential. Synthetic credentials were not printed or retained in evidence.
- Review found that profile avatar reads combined an API URL containing `/api/v1` with an Axios instance already based at `/api/v1`. `DashboardView.vue` now reads the relative `/me/avatar` route. Recheck showed the image loaded, the error banner cleared, and removal restored the default avatar.
- The account table and grade table remain vertically scrollable at 1280x800; no controls overlap. Browser process and screenshots are distinct from the older static-fixture captures, which remain historical visual evidence only.

- The final UI pass removed the repeated generic “按当前账户权限显示” subtitle outside the overview. Profile/password text inputs expose accessible names; ego-browser confirmed Tab focus is visibly outlined on the next action. Final code-matched captures are `ego/final-copy-real-api-accounts-1440x900.png` and `ego/final-copy-real-api-profile-1280x800.png`.
