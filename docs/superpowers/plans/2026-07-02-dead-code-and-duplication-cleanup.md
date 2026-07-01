# Dead Code and Duplication Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove proven dead code and reduce repeated menu-save orchestration while preserving behavior.

**Architecture:** Keep existing public HTTP and persistence boundaries intact. Delete only symbols with no consumer, then consolidate service-level operation sequencing behind a private helper and verify through the existing integration suite.

**Tech Stack:** Java 21, Spring Boot 3.5, Gradle, React 19, Vitest, ESLint, Vite

---

### Task 1: Remove Unreferenced Symbols

**Files:**
- Delete: `src/main/java/com/example/admin/menu/dto/adminmenu/MenuBulkRequest.java`
- Delete: `src/main/java/com/example/admin/menu/dto/adminmenu/BulkInsertResponse.java`
- Modify: `frontend/src/api/adminApi.js`

- [x] Confirm each symbol has no source, test, mapper XML, or active documentation consumer with `rg`.
- [x] Delete the two backend DTO files.
- [x] Remove `getUser`, `getRole`, and `saveSqlLogTiming` from `adminApi.js`.
- [x] Run `.\gradlew.bat test` and `npm test`.

### Task 2: Consolidate Menu Save Sequencing

**Files:**
- Modify: `src/main/java/com/example/admin/menu/service/JpaAdminMenuService.java`
- Test: `src/test/java/com/example/admin/api/JpaAdminApiTests.java`

- [x] Use the passing grid-save integration tests as the regression lock.
- [x] Reuse `GridSaveExecutor`'s existing null/empty handling instead of repeating service guards.
- [x] Express delete, create, and update as ordered result-producing operations.
- [x] Merge the ordered results without changing the `MenuGridSaveResponse` mapping.
- [x] Run `.\gradlew.bat test`.

### Task 3: Completion Audit

**Files:**
- Modify only if verification exposes a cleanup regression.

- [x] Run `rg` again for the removed symbols and inspect `git diff --check`.
- [x] Run `.\gradlew.bat test`.
- [x] Run `npm test`, `npm run lint`, and `npm run build` from `frontend`.
- [x] Review the final diff for behavior changes outside the documented scope.
