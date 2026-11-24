# Independent Workspace Preview Evidence

Captured with ego-browser 2.2.1 on 2026-11-24 from `http://127.0.0.1:5174/new-workspace-preview.html`.

The screenshots document layout only. The browser used a localStorage role display marker without an auth token. No API fixture, authenticated API request, or write operation was used. Empty-state visuals are therefore not evidence of real backend data rendering.

| View | Evidence |
|---|---|
| ADMIN overview, 1440x900 | `admin-shell-1440x900.png` |
| ADMIN overview, 1280x800 | `admin-shell-1280x800.png` |
| ADMIN overview, 1920x1080 | `admin-shell-1920x1080.png` |
| TEACHER navigation, 1440x900 | `teacher-shell-1440x900.png` |
| COUNSELOR navigation, 1440x900 | `counselor-shell-1440x900.png` |
| STUDENT navigation, 1440x900 | `student-shell-1440x900.png` |

ego-browser confirmed the role-specific navigation labels and ADMIN student-list empty state. At 1280x800, 1440x900 and 1920x1080, `document.documentElement.scrollWidth` equaled `clientWidth`. Tab focus reached the brand link. The authenticated workflows, populated data, loading/error states and operation outcomes were not reviewed.

After visual feedback on 2026-11-24, the full topbar was removed. The page-title row has a right-side avatar; clicking it opens a rounded logout menu. The transparent sidebar shows the signed-in user's name as the profile link, separated from the lower edge by a light divider. Current-user role labels are removed from the overview/profile. The main white surface reaches the viewport bottom with square lower corners; radii are 16px outer, 14px inner panels, 12px controls/menu and 8px menu item. Enabled primary buttons use blue with white text; disabled primary buttons use light blue. Active navigation uses bold text and a subtle neutral mask only. ego-browser confirmed no topbar node, avatar/logout menu behavior, bottom-filling white surface, name/profile state, button colors and no horizontal overflow at 1280x800, 1440x900, and 1920x1080. The profile screen was inspected at 1440x900. Updated profile capture: `profile-shell-1440x900.png`.

For the teaching-data workflow, the create-entry controls now form a horizontal scrolling row above the department directory. Each labeled button opens its corresponding second-level form dialog; the trailing ellipsis opens the same choices as an icon-only overflow menu with an accessible label and tooltip. ego-browser verified the action row, directory order, department dialog, overflow menu and dropdown radius at 1440x900. An error raised by the remote student picker appears in a fixed top-right toast and disappeared after 3.2 seconds; screenshots: `foundation-actions-1440x900.png` and `foundation-dialog-1440x900.png`.
