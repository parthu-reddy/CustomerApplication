# API Contract Source of Truth Rule

**CRITICAL ARCHITECTURE DECISION**
- Do **NOT** maintain a monolithic `openapi.yaml` at the root of the project.
- The **Single Source of Truth** for API contracts is the Java code itself in each microservice.
- OpenAPI specifications (`openapi.json`) MUST be generated dynamically from the code (e.g., using `OpenApiGenerationTest` via `@SpringBootTest` with mocked infrastructure) to ensure they are always accurate and up to date.
- Frontend clients and other consumers must rely on these generated, service-specific `openapi.json` artifacts rather than a central manual YAML file.
