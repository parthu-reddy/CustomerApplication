---
description: End-to-End Data Integrity Verification
---

# Workflow: End-to-End Data Integrity Verification

> **Deployment context**: See [deployment-context.md](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/.agents/rules/deployment-context.md) for Cloudflare domain, Oracle VM IP, SSH commands, and ports.
> **Operational rules**: See [data-integrity-rules.md](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/.agents/rules/data-integrity-rules.md) for sandbox configuration and global mandates.

**CRITICAL**: All HTTP requests MUST target `https://eng-restricted-dad-separately.trycloudflare.com/`, never `localhost`.

## Operational Mandate

Execute a comprehensive, zero-mistake audit of all data movement — from the UI, through interservice boundaries, to database persistence and Kafka event streaming. Prioritize generating Artifacts (Plans, Diffs, Test Results) over raw logs. On discovering drift or vulnerabilities, halt immediately, generate an Implementation Plan, and await approval.

## Phase 1: Full-Stack Schema Synchronization (Subagent Alpha)

* **Objective:** Verify single-source-of-truth validation logic via static analysis.
* **Skill:** Activate `zod-schema-sync`. Scan frontend and backend. Confirm shared schema imports, universal `.safeParse()`, and `z.infer` type inference.
* **Test:** Run frontend unit tests via terminal (Jest/Vitest) to prove malformed data is rejected. Do NOT use BrowserMCP.

## Phase 2: Interservice API Contract Enforcement (Subagent Beta)

* **Objective:** Guarantee microservices honor shared interface agreements.
* **Skill:** Activate `api-contract-enforcer`. Execute Pact CDC tests and Schemathesis:
  ```bash
  schemathesis run --checks all openapi.yaml --base-url https://eng-restricted-dad-separately.trycloudflare.com/
  ```
* **Output:** Generate a Test Report Artifact proving zero unhandled 500-level errors.

## Phase 3: Persistence & Distributed Transactions (Subagent Gamma)

* **Objective:** Eliminate the dual-write problem. Verify transactional outbox and event-driven cache invalidation.
* **Skill:** Activate `audit-outbox-pattern`. Verify ACID boundaries, outbox schema, and Debezium config.
* **DB Check:** Verify PostgreSQL WAL level via SSH (see deployment-context rule for command).
* **Cache:** Ensure Redis invalidation is event-driven via Kafka consumers, not TTL-based.

## Phase 4: Edge Cases and Resilience Validation

* **Objective:** Ensure graceful handling of network degradation, data poisoning, and consumer latency.
* **Idempotency:** Audit consumers for `idempotency_key` tracking via Redis or DB unique constraints.
* **DLQ:** Verify poison-pill messages route to DLQ topics with offset commits on the main topic.

## Final Output

Synthesize all subagent findings into a master **System Integrity Report Artifact**:
- **Critical Faults** — dual-writes, missing outbox, unhandled 500s
- **Validation Drifts** — schema mismatches, missing `.safeParse()`
- **Passed Checks** — verified patterns

If critical faults exist, auto-generate a Code Diff and await approval.

## Session Management

| Command | Function |
| :---- | :---- |
| `esc` | Instantly interrupt the agent's current turn. |
| `/rewind` or `/undo` | Roll back to a previously stable checkpoint. |
| `/fork` | Branch the conversation to test speculative fixes in isolation. |
| `/resume` | Merge a successful fork back into the main timeline. |
