---
name: zod-schema-sync
description: Audits frontend-to-backend validation schema synchronization. Use when verifying that UI form validation, API payload validation, and TypeScript types all derive from a single source of truth (Zod schemas in a shared package). Triggers on mentions of 'validation drift', 'schema sync', 'Zod audit', 'form validation', 'type safety', 'frontend backend mismatch', 'shared types', 'safeParse'.
---

# Zod Schema Synchronization Auditor

> **Deep architectural context**: See [references/validation-drift-architecture.md](references/validation-drift-architecture.md) for the full explanation of validation drift, Zod implementation patterns, and Zod v4 features.
> **Deployment details**: See the workspace rule [deployment-context.md](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/.agents/rules/deployment-context.md).

## Agent Instructions

1. **Find all Zod Imports**: Use code search tools to find every file importing Zod across frontend and backend directories.
2. **Detect Validation Drift**: If separate Zod schemas for the same domain entity exist in both frontend and backend, immediately flag this as a **critical drift vulnerability**.
3. **Propose Consolidation**: Generate a Code Diff artifact proposing consolidation into a single shared workspace/monorepo package.
4. **Verify Safe Runtime Parsing**: Ensure all incoming API payloads and form submissions use `.safeParse()` instead of `.parse()`.
5. **Verify Schema Composition**: Check that update payloads (PUT/PATCH) derive schemas from the base entity using `.partial()`, `.merge()`, or `.pick()`.
6. **Verify Advanced Constraints**: Check for cross-field validation via `.superRefine()` or `.refine()`.
7. **Verify Zod v4 Features**: Ensure use of `z.interface()` over `z.lazy()`, `.toJSONSchema()` for OpenAPI sync, and `@zod/mini` for edge runtimes.
8. **Terminal-Based Unit Testing**: Run frontend unit tests via the terminal (Jest/Vitest) to prove UI components correctly reject malformed data per the shared schema.
9. **API Endpoint Validation**: When validating responses against schemas, target `https://eng-restricted-dad-separately.trycloudflare.com/`, never `localhost`.

## Constraints

- **Strictly omit** any BrowserMCP or browser automation. Rely entirely on static code analysis and terminal-based testing.
- **Halt immediately** if schema drift is detected. Generate an Implementation Plan and await approval.
