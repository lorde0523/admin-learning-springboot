# React Backend Learning Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Vite React learning console for every existing admin API, including SQL and timing inspection.

**Architecture:** Keep Axios transport, React Query server state, Atomic Design UI, and route pages in separate dependency layers. A shared Axios interceptor derives `X-Ui-Id` from the current route so individual requests never manage tracing headers.

**Tech Stack:** React, Vite, JavaScript, React Router, Axios, TanStack React Query, AG Grid Community, Vitest, React Testing Library, MSW, ESLint

---

### Task 1: Project foundation and documentation

- [x] Create the Vite, ESLint, and Vitest configuration.
- [x] Document every Atomic Design directory and its dependency rules.
- [x] Install dependencies and verify the empty test environment.

### Task 2: HTTP and server-state boundaries

- [x] Write failing tests for route-based GET headers and normalized errors.
- [x] Implement the Axios client and route UI ID registry.
- [x] Write domain API functions with no React dependencies.
- [x] Wrap API functions with domain query and mutation hooks.

### Task 3: Shared Atomic components and application shell

- [x] Test and implement accessible atoms and molecules.
- [x] Test and implement the workbench template, navigation, and session status.
- [x] Add shared loading, empty, error, dialog, and notification behavior.

### Task 4: User and role workbenches

- [x] Test and implement user search, CRUD, details, and role assignment.
- [x] Test and implement role CRUD, details, and menu assignment.
- [x] Verify mutation-specific query invalidation.

### Task 5: Menu comparison and SQL inspector

- [x] Test and implement identical JPA/MyBatis paging parameters.
- [x] Test the menu delta reducer before implementing grid editing.
- [x] Implement grid save feedback and refresh behavior.
- [x] Test and implement raw response and SQL trace panels.

### Task 6: Completion verification

- [x] Run focused and complete frontend tests.
- [x] Run ESLint and the production build.
- [x] Run the Spring Boot test suite.
- [x] Perform a browser UI smoke test; record Oracle/SSO/Redis integration as environment-dependent.
