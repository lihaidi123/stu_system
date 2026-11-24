# Interface Design

Use one Chinese-language application shell with navigation derived from role, as specified in section 10. Keep dense work tables scannable, use restrained surfaces and clear hierarchy, and prioritize keyboard operation and responsive behavior. Avoid decorative dashboard content and repeated explanatory copy.

Every workflow must distinguish loading, empty, error, retry, permission denied, validation failure, and expired login states. Narrow screens may scroll within wide tables but must not cause page-wide horizontal overflow.

Before UI implementation, search the live LeiBox catalog for a small number of Vue-compatible components relevant to navigation, filtering, or task feedback, verify licenses/dependencies, and record findings in `docs/COMPONENT_SOURCES.md`. The independent 2026-11-24 workspace preview reuses existing Element Plus controls; current LeiBox navigation candidates were not adopted because their motion/material treatment does not fit dense admin workflows. The new preview is not yet wired into `App.vue` and awaits review before replacement.
