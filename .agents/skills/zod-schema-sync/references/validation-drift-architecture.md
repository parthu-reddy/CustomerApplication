# Validation Drift Architecture & Zod Implementation Patterns

This reference document provides the deep architectural context for the `zod-schema-sync` skill.

## The Problem: Validation Drift

The first critical boundary where data integrity often fails is the transition from the client-side user interface to the backend API gateway. A pervasive architectural anti-pattern in full-stack applications is **validation drift**, which occurs when multiple layers of the application attempt to define the same validation rule independently. Typically, a frontend form library defines required fields and length constraints, the backend API route handles payload validation using a separate library, and TypeScript types are written in isolation to represent the shape of the data.

This duplication creates a massive synchronization debt. Over time, these decoupled definitions inevitably drift. The frontend may accept a string value that the API subsequently rejects with a 400 Bad Request, leading to a degraded user experience. Conversely, and far more dangerously, the backend may accept a malformed payload because its validation logic lagged behind a newly implemented frontend feature, injecting corrupted data deep into the domain model. Furthermore, standalone TypeScript types imply compile-time guarantees that runtime validation does not actually enforce, creating a false sense of security for developers.

## The Solution: Zod as Single Source of Truth

The definitive architectural solution to validation drift is the implementation of a single-source-of-truth validation schema utilizing libraries such as Zod. Zod is a zero-dependency, lightweight, TypeScript-first validation library that allows developers to define domain validity once in a singular schema, and let every runtime (UI, API, and compiler) consume that exact schema.

Zod's most powerful feature is its automatic TypeScript type inference. By declaring a schema (e.g., `const UserSchema = z.object({...})`), developers can automatically infer the static type (`type User = z.infer<typeof UserSchema>`), completely eliminating the need to manually keep static types and runtime schemas in sync.

## Audit Verification Matrix

| Architectural Pattern | Zod Implementation Mechanism | Agent Audit Verification Task |
| :---- | :---- | :---- |
| **Shared Domain Ownership** | Monorepo shared packages | Verify that all UI components and API controllers import schemas from a unified shared-types directory rather than defining them locally. |
| **Safe Runtime Parsing** | schema.safeParse(data) | Ensure that all incoming API payloads and form submissions utilize `.safeParse()` instead of `.parse()`. This guarantees that validation failures return a structured error object with precise field paths rather than throwing unhandled exceptions that can crash the Node.js process. |
| **Schema Composition** | .partial(), .merge(), .pick() | Verify that update payloads (e.g., PUT/PATCH requests) derive their schemas from the base domain entity using Zod composition methods, rather than manually rewriting an entirely new schema. |
| **Advanced Constraints** | .superRefine(), .refine() | Check for cross-field validation logic (e.g., ensuring an end date is strictly after a start date, or passwords match) handled natively within the schema. |

## Zod v4 Enhancements

A sophisticated audit must also verify:

- **`z.interface()`**: More accurate alignment with TypeScript's handling of optional vs. undefined values, bypassing the limitations of older `z.lazy()` recursive types.
- **`.toJSONSchema()`**: Allows runtime Zod schemas to be converted directly into JSON Schema, keeping API documentation (OpenAPI) and downstream form generation perfectly synchronized without manual intervention.
- **`@zod/mini`**: For performance-sensitive edge runtimes or serverless environments, minimizes bundle size while retaining core parsing capabilities.
