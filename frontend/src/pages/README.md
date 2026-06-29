# Pages

Pages are React Router entry points. They connect React Query hooks and route state to Atomic Design components.

- Pages may import templates, organisms, and query hooks.
- Keep detailed rendering and reusable interactions in lower Atomic layers.
- Do not import Axios directly; use `queries/`.
- A page owns the route-level loading, error, and selection flow.

