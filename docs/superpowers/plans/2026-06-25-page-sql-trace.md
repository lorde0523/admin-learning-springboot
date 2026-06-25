# User-Scoped Page SQL Trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Accumulate MyBatis/JPA query SQL by authenticated user and page, preserve the list while writing is paused, and hide all rows before a durable user-scoped clear marker.

**Architecture:** `SqlCaptureRequestFilter` derives `username` from Spring Security and combines it with `pageId` and `requestId`. JDBC interception writes `QUERY` events, Nexacro writes `TIMING` events, and reset writes `CLEAR` events into date-partitioned JSONL. The list API replays events for the current username and page.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security session authentication, JDBC dynamic proxies, Jackson JSONL, JUnit 5, MockMvc, H2

---

### Task 1: Authenticated Request Context

- [x] Add `username` to `SqlCaptureContext`.
- [x] Resolve username with `LoginUsers.currentUsername()`.
- [x] Skip tracing when there is no authenticated `LoginUser`.
- [x] Preserve page and pause headers.
- [x] Verify context cleanup after every request.

### Task 2: Event-Based JSONL Storage

- [x] Add `QUERY`, `TIMING`, and `CLEAR` event types.
- [x] Store username, requestId, pageId, event time, SQL time, SQL text, and optional client times.
- [x] Scan all date-partitioned files in chronological order.
- [x] Filter events by exact username and pageId.
- [x] Clear accumulated queries on the latest matching CLEAR event.
- [x] Merge TIMING values into every QUERY with the same requestId.
- [x] Ignore malformed and legacy ownerless lines.
- [x] Keep append and lookup under the same process-local I/O lock.

### Task 3: User-Scoped APIs

- [x] Replace requestId list lookup with `GET /api/sql-logs?pageId=...`.
- [x] Add `POST /api/sql-logs/timing`.
- [x] Add `DELETE /api/sql-logs?pageId=...`.
- [x] Resolve username only from the authenticated session.
- [x] Return 401 when no authenticated LoginUser exists.
- [x] Reject timing updates for requests not owned by the current user and page.

### Task 4: Integration Behavior

- [x] Verify MyBatis queries accumulate by user and page.
- [x] Verify JPA queries use the same accumulation path.
- [x] Verify user1 logs are invisible to user2.
- [x] Verify paused requests do not append and previous rows remain visible.
- [x] Verify clear hides previous rows.
- [x] Verify new queries appear after clear.
- [x] Verify client timing is merged into all SQL rows for one business request.
- [x] Verify unauthenticated requests are not traced.

### Task 5: Completion

- [x] Run SQL trace tests.
- [x] Run the complete test suite.
- [x] Inspect JSONL QUERY, TIMING, and CLEAR events.
- [x] Run independent code review.
- [ ] Commit with Lore trailers.
- [ ] Push the current branch.
