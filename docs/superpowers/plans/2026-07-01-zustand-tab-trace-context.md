# Zustand Tab Trace Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive GET SQL trace headers from the active Zustand-managed tab.

**Architecture:** A standalone Zustand store owns tab metadata. The Atomic Design tab organism synchronizes the store with React Router, while the Axios interceptor reads the store synchronously through `getState()`.

**Tech Stack:** React, JavaScript, Zustand, React Router, Axios, Vitest, React Testing Library

---

### Task 1: Tab store

- [x] Write a failing store test for open, deduplicate, select, and close behavior.
- [x] Run `npm test -- src/stores/useTabStore.test.js` and confirm the missing-module failure.
- [x] Implement `frontend/src/stores/useTabStore.js`.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Tab UI and routing

- [x] Write a failing component test for tab creation and selection.
- [x] Implement `WorkspaceTabs` and connect it from `AppLayout`.
- [x] Confirm selecting and closing tabs navigates to the expected route.

### Task 3: Axios trace context

- [x] Extend the existing interceptor test to expect the active tab `uiId`.
- [x] Inject a tab-context reader into `createHttpClient`.
- [x] Retain pathname lookup as initialization fallback and verify GET-only behavior.

### Task 4: Usage guidance and verification

- [x] Add a README example showing screen registration and direct store access.
- [x] Run `npm test`, `npm run lint`, and `npm run build`.
- [x] Run `gradlew test` to confirm the backend remains unchanged.
