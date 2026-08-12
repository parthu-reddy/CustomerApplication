# Outbox Pattern, Dual-Write Prevention & Resilience Architecture

This reference document provides the deep architectural context for the `audit-outbox-pattern` skill.

## The Dual-Write Problem

The most mathematically complex facet of ensuring end-to-end data integrity occurs at the infrastructure boundary, where data must be securely persisted to a relational database (e.g., PostgreSQL) while simultaneously emitting an asynchronous event to a message broker (e.g., Apache Kafka) to inform downstream systems or update in-memory caches (e.g., Redis).

Attempting to perform both operations sequentially within standard application code introduces a catastrophic architectural flaw known as the "Dual-Write Problem." If a microservice executes an INSERT statement into a database and commits the transaction, and subsequently attempts to publish a FundsDeposited event to Kafka, a network partition or process crash occurring between these two operations will leave the system in a permanently inconsistent state. The database will reflect a financial transaction that the rest of the event-driven architecture is entirely blind to, rendering downstream read models, search indexes, and cache layers completely corrupted.

## The Transactional Outbox Pattern and Change Data Capture

To achieve fault tolerance and high availability, the Transactional Outbox Pattern paired with Change Data Capture (CDC) technologies like Debezium must be rigorously enforced.

The Transactional Outbox Pattern leverages the relational database's internal ACID properties. Instead of publishing an event directly to the broker over a fragile network connection, the application writes the event payload as a physical row in a dedicated outbox table residing within the same database schema as the primary business entities.

### Verification Sequence

1. **ACID Transaction Boundaries:** The primary business write (e.g., `INSERT INTO orders`) and the event generation (e.g., `INSERT INTO outbox`) must be wrapped within a single, indivisible database transaction. If the transaction fails, neither table is updated; if it succeeds, both are updated atomically.
2. **Database Schema:** The outbox table must have: `id` (UUID), `aggregate_id`, `aggregate_type`, `idempotency_key`, `payload` (JSONB), and a timestamp.
3. **Logical Replication:** The PostgreSQL WAL level must be set to `logical` — a strict prerequisite for CDC tools to capture row-level changes.
4. **Debezium Connector:** Debezium reads the PostgreSQL WAL and guarantees at-least-once delivery of outbox rows to Kafka topics. The `table.include.list` must contain the outbox schema. A publication must be active: `CREATE PUBLICATION dbz_publication FOR TABLE "application".outbox WITH (publish = 'insert');`

## Event-Driven Cache Invalidation

A common, flawed approach to caching involves setting randomized Time-To-Live (TTL) expirations on Redis keys to prevent cache stampedes (where millions of keys expire simultaneously, bombarding the backend). While random TTLs mitigate stampedes, they negatively impact the cache hit rate and allow stale data to persist until the TTL naturally expires.

The correct approach is **event-based cache invalidation**: when Debezium emits an outbox event detailing a modified database row, a dedicated, asynchronous Kafka consumer catches that event and proactively evicts or updates the corresponding key in Redis. This ensures the cache is explicitly tied to the absolute truth of the database transaction, achieving perfect eventual consistency.

## Idempotency and Consumer Retry Storms

Because Kafka and Debezium operate on an "at-least-once" delivery guarantee, network latency or consumer crashes may cause the exact same outbox event to be delivered multiple times. Consumer codebases must implement **Idempotency** — the mathematical property guaranteeing that repeated application of an operation yields the same result as a single application.

The incoming message payloads must include a unique, deterministic `idempotency_key`. The consumer intercepts this key, checking it against a distributed lock (via Redis) or a unique constraint in the consumer's local database before processing. If a financial transaction event is replayed, the consumer silently acknowledges the offset and drops the duplicate message rather than debiting an account twice. Alerting must be established for "retry storms" and throttling thresholds.

## Dead Letter Queues (DLQ) and Poison Pills

If a message placed on a Kafka topic violates the structural expectations of the consumer — perhaps due to an unversioned schema change bypassing the contract tests — it is classified as a "poison pill." A naive consumer will attempt to deserialize the payload, throw a runtime exception, crash, restart, and immediately read the same message again, effectively blocking the entire partition indefinitely.

All asynchronous consumers must implement robust error handling:
- Deserialization logic must use `safeParse` equivalents (Zod or Protobuf deserializers).
- On parsing failure, route the malformed payload to a dedicated Dead Letter Queue (DLQ) topic.
- Commit the consumer offset for the main topic, allowing processing of healthy messages.
- AsyncAPI Schema Registry workflows must govern message schemas globally.
