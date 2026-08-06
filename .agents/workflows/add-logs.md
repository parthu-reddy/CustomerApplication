---
description: Add logs that are needed
---

You are an expert Java Spring Boot architect. Your task is to analyze the entire microservices codebase within this workspace and systematically instrument it with standardized, production-grade logging. 

Traverse all microservice directories and apply the following logging standards to the code:

1. Logging Framework (Lombok Strict): 
- You must exclusively use Lombok's `@Slf4j` annotation at the class level for all logger instantiations. 
- Do not generate or use `LoggerFactory.getLogger(...)` boilerplate anywhere in the codebase.

2. Context & Traceability (MDC):
- For all REST Controllers and message listeners (e.g., Kafka/RabbitMQ), ensure MDC (Mapped Diagnostic Context) is populated with a `traceId` or `correlationId` at the entry point of the request/message and cleared in a `finally` block or via a web filter. 

3. Log Level Standardization:
- ERROR: Catch blocks and global exception handlers. Always include the exception object (`log.error("Failed to process transaction", e);`) to preserve the stack trace.
- WARN: Non-fatal issues, retries, or unexpected state that doesn't halt execution.
- INFO: Application lifecycle events, major business logic milestones, and state changes (e.g., "Order created successfully with ID: {}"). Do not log payloads here.
- DEBUG: Request/response payloads, database query parameters, and detailed algorithm steps. 

4. Security & Compliance:
- Never log PII (Personally Identifiable Information), credentials, bearer tokens, or sensitive payload data. Mask these fields if logging entire objects.

5. Execution Plan:
- Analyze the architectural layout (controllers, services, repositories, configurations).
- Inject the `@Slf4j` annotations and corresponding log statements into all necessary files.
- Ensure the logs are structured in a way that is easily ingestible by centralized logging stacks (e.g., JSON formatting for ELK/Kibana).
- Output a summary of the modified files and any missing centralized logging dependencies (like `logstash-logback-encoder`) you detected.