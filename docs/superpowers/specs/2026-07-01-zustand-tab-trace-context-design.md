# Zustand Tab Trace Context Design

## Goal

Use the active multi-tab screen's `uiId` for SQL trace requests while preserving the Redis key format `query:{username}:{uiId}`.

## Design

- `useTabStore` owns ordered tabs and `activeTabId`.
- A tab contains `id`, `title`, `path`, and `uiId`.
- Opening an existing path selects it instead of creating a duplicate.
- Selecting or closing a tab updates React Router navigation.
- The Axios request interceptor reads `useTabStore.getState()` for GET requests only.
- Direct URL entry uses the existing pathname registry until the layout creates the matching tab.
- SQL log requests keep `X-Sql-Capture-Paused: true`; Redis key composition does not change.

## Tests

- Store open, deduplicate, select, and close behavior.
- Tab bar navigation and close fallback.
- GET headers use the active tab's `uiId`; mutations do not.
- Pathname fallback works before tab initialization.

