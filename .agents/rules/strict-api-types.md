# Strict API Typing Rule

When working on `FoodDeliveryAppUI`, you MUST adhere to the following strict API integration practices:

1. **No Any or Ignored Types**: Never use `any`, `@ts-ignore`, or forced type casting like `as unknown as Type`.
2. **Use Zodios Schemas**: Always import and use the strongly typed Zodios schemas generated in `src/api/generated/schemas/` to construct or consume UI payloads.
3. **No Manual Unwrapping**: Do not manually unpack response bodies (e.g., using `Array.isArray()`). Instead, type components using the standard `PageResponseDto` wrapper structure defined by the OpenAPI spec.
4. **Fix the Source**: If an API response is missing a type or incorrectly generated, do NOT override or patch it in the UI codebase. Instead, you must fix the source of truth by updating the Java backend controller (using `@RestController` and `ResponseEntity`) to generate the schema correctly via `openapi.json`.
