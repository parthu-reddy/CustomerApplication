---
name: audit-outbox-pattern
description: Audits persistence layer, distributed transactions, and event broker consistency. Use when verifying that the Transactional Outbox Pattern is correctly implemented, dual-write problems are eliminated, Kafka event delivery is reliable, and cache invalidation follows event-driven patterns. Triggers on mentions of 'outbox pattern', 'dual write', 'transactional outbox', 'Debezium', 'CDC', 'change data capture', 'cache invalidation', 'event consistency', 'Kafka reliability', 'at-least-once', 'idempotency', 'dead letter queue', 'DLQ', 'poison pill', 'retry storm'.
---

# Audit Outbox Pattern

> **Deep architectural context**: See [references/outbox-architecture.md](references/outbox-architecture.md) for the full explanation of the dual-write problem, transactional outbox, CDC, cache invalidation, idempotency, and DLQs.
> **Deployment details**: See the workspace rule [deployment-context.md](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/.agents/rules/deployment-context.md).

## Agent Instructions

### Outbox Pattern Verification

1. **Verify ACID Transaction Boundaries**: Audit backend ORM code to ensure the primary business write and the outbox insert are in a single, indivisible database transaction.
2. **Verify Database Schema**: Inspect migration files to ensure the outbox table has: `id` (UUID), `aggregate_id`, `aggregate_type`, `idempotency_key`, `payload` (JSONB), and a timestamp.
3. **Verify Logical Replication**: Execute `SHOW wal_level;` on the PostgreSQL instance via SSH:
   ```bash
   ssh -i /Users/parthureddy/Documents/OracleSSH/ssh-key-2026-08-16.key ubuntu@140.245.234.137 \
     "cd 'Food Delivery.nosync/Deployment' && docker compose exec -T -e PGPASSWORD=password postgres psql -U postgres -c 'SHOW wal_level;'"
   ```
   Verify the WAL level is `logical`.
4. **Audit Debezium Connector**: Inspect the Debezium Kafka Connect configuration JSON. Ensure `table.include.list` contains the outbox schema and a publication is active.

### Cache Invalidation Verification

5. **Verify Event-Driven Invalidation**: Ensure Redis caches are evicted via asynchronous Kafka consumers reacting to Debezium outbox events, NOT via TTL-based expiration or direct API calls.

### Resilience Verification

6. **Audit Idempotency**: Ensure consumers check `idempotency_key` against a distributed lock (Redis) or unique DB constraint before processing. Verify alerting for "retry storms."
7. **Audit Dead Letter Queues**: Verify deserialization uses `safeParse` equivalents. On parsing failure, route to a DLQ topic and commit the main topic offset. Check for AsyncAPI Schema Registry governance.

## Constraints

- Database commands require SSH into the Oracle VM (see deployment-context rule).
- **Halt immediately** on dual-write vulnerabilities. Generate an Implementation Plan and await approval.
