# Dead Code and Duplication Cleanup Design

## Goal

Remove code that has no runtime or test consumer, and simplify repeated menu-grid save orchestration without changing API behavior.

## Scope

- Delete backend DTOs with no Java, XML, test, or documentation consumer:
  `MenuBulkRequest` and `BulkInsertResponse`.
- Delete frontend API exports with no application or test consumer:
  `getUser`, `getRole`, and `saveSqlLogTiming`.
- Replace the three repeated null/empty guards in `JpaAdminMenuService.saveGrid` with one
  ordered operation pipeline that preserves delete → create → update behavior.
- Preserve MyBatis mapper interfaces and row classes because mapper XML references them by
  fully qualified string names even where Java call sites are absent.
- Preserve extension seams such as `SqlValueMasker` and `RedisStore`; their implementations
  are injected and covered by tests.

## Behavior Contract

- Menu-grid conflict validation still runs before persistence.
- Delete operations run before creates, and creates before updates.
- Null or empty row groups perform no repository mutation and contribute zero counts.
- Existing skip/message and strict failure modes remain unchanged.
- Existing HTTP routes, request/response payloads, SQL tracing, and frontend navigation remain unchanged.

## Testing

Existing backend API tests cover grid-save ordering, conflict handling, strict failures, and DTO-key
deletion. Existing frontend API tests cover the exported functions that remain in use. Verification
will run backend tests plus frontend tests, lint, and production build after cleanup.
