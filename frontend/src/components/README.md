# Atomic Design Rules

UI dependencies flow in one direction:

```text
atoms -> molecules -> organisms -> templates -> pages
```

Components never import from a layer to their right. HTTP and server state live outside this tree in `api/` and `queries/`. Pages may connect query hooks to templates; lower layers receive data and callbacks through props.

Use the lowest layer that fully describes a component. Do not generalize a domain-specific organism merely to make it appear reusable. Keep tests next to the component they describe.

