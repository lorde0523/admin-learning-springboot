# React Backend Learning Workbench Design

## Goal

Build a JavaScript React application that exercises the existing Spring Boot APIs and makes request, response, timing, and SQL behavior visible.

## Architecture

- The application lives in `frontend/` and runs with Vite.
- Axios owns HTTP transport. TanStack React Query wraps the HTTP functions and owns server state.
- Pages call query hooks rather than importing Axios or API modules.
- UI composition follows Atomic Design: atoms, molecules, organisms, templates, then pages.
- AG Grid Community is the only visual component dependency. Forms, dialogs, navigation, and feedback use React and CSS.

## Workbenches

- Dashboard: authenticated user, backend reachability, and SQL trace availability.
- Users: search, CRUD, audit details, and role assignment.
- Roles: CRUD and menu assignment.
- Menus: JPA/MyBatis paged comparison and delta grid save.
- Inspector: last response metadata, raw JSON, accumulated SQL, timing, and clear action.

## Request Rules

- An Axios request interceptor adds the route-specific `X-Ui-Id` header to every GET request.
- SQL log requests also receive `X-Sql-Capture-Paused: true`.
- Cookies are sent with every request.
- API errors are normalized separately from network and client response-shape errors.
- Existing SSO authentication is assumed; the frontend only reads `/api/auth/me`.

## Constraints

- Use JavaScript rather than TypeScript.
- Do not change backend APIs or database schemas.
- New menu rows require a positive ID because the current backend contract requires one.
- Keep comments focused on responsibility and non-obvious decisions.

