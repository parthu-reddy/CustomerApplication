---
description: Implement micrometer tracing to all microservices
---

You are an expert Java Spring Boot and Distributed Systems Architect. Your task is to analyze the entire microservices workspace and systematically implement production-grade Distributed Tracing using Spring Boot 3's Micrometer Tracing.

Traverse all microservice directories and apply the following tracing standards:

1. Dependency Management:
- Ensure `spring-boot-starter-actuator` is present in all services.
- Inject the necessary Micrometer Tracing dependencies into every `pom.xml` or `build.gradle`. Use OpenTelemetry as the default bridge and exporter (`micrometer-tracing-bridge-otel` and `micrometer-tracing-reporter-otlp`).

2. Configuration & Sampling:
- Update all `application.yml` or `application.properties` files to enable tracing (`management.tracing.enabled=true`).
- Configure a robust sampling strategy. Set `management.tracing.sampling.probability=1.0` for development/local profiles to trace everything. For production profiles, enforce a lower sampling rate (e.g., `0.1` or a custom sampler) to prevent trace generation from degrading system performance and increasing latency under high load.
- Set the backend exporter endpoint (e.g., OTLP collector URL) in the configuration.

3. Log Correlation (MDC):
- Ensure the logging pattern in all services includes the trace and span IDs (e.g., `logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]`).
- Verify that this integrates seamlessly with the SLF4J/Lombok setup previously implemented, allowing centralized logging stacks to correlate logs across service boundaries natively.

4. Context Propagation & Instrumentation:
- Ensure all synchronous and asynchronous inter-service communication clients (e.g., `RestTemplate`, `WebClient`, `FeignClient`) are properly instantiated as Spring Beans so Micrometer auto-instruments them with W3C Trace Context headers.
- If Kafka, RabbitMQ, or other message brokers are used, ensure the messaging factories/templates are configured to propagate trace context (e.g., enabling observation on Kafka listeners).

5. Custom Observability:
- For highly critical, complex business logic that isn't automatically instrumented via HTTP/Messaging, inject `ObservationRegistry` or use the `@Observed` annotation to create custom spans. 
- Ensure custom span names remain low-cardinality (e.g., do not inject UUIDs into the span name; use tags instead).

6. Execution Plan:
- Analyze the build files and inject dependencies.
- Apply the tracing configuration properties to all application configurations.
- Audit communication beans for proper auto-instrumentation setup.
- Output a summary of the modified files and highlight any specific services where context propagation might require manual intervention (e.g., custom threading where `ContextSnapshot` is needed).