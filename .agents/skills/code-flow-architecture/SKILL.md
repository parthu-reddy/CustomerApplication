---
name: code-flow-architecture
description: Explains the architecture, Saga orchestration patterns, and the event-driven code flow in the Customer Application. Use this to understand how orders are managed.
---

# Code Flow & Architecture Guide (Customer Application)

This document describes the architectural patterns used in the Customer Application, acting as the Order Management and Customer Gateway for the Food Delivery platform.

## Architecture (Microservice)

The backend has been refactored from a Modular Monolith into independent microservices. This application (CustomerApplication) runs on Java 21 and Spring Boot 3.3.0.

The codebase is organized under `com.fooddelivery`:
- **`customer`**: Manages auth (`AuthController`), location discovery (`PlacesController`), restaurant search via external integration (`CustomerRestaurantController`), and cart/order initiation (`OrderController`).
- **`order`**: Orchestrates distributed Saga transactions (`OrderSagaOrchestrator`), Outbox pattern implementation, Ledger immutable double-entry accounting with optimistic locking (`LedgerService`), and Vyapar constant-time HMAC webhook validation (`WebhookController`).

**Key Engineering Standards:**
- **Database**: The database schema is initialized using Flyway (`customer_db`). All initial tables and production indexes are squashed into a single `V1__init_schema.sql` file.
- **Kafka Listener Exception Handling**: Kafka `@KafkaListener` methods must NEVER silently catch and swallow exceptions. They log and rethrow exceptions.
- **Transaction Proxy Bypass**: AOP proxy bypass issues must be avoided. Methods annotated with `@Transactional` cannot be called internally from within the same bean (self-invocation).

## The Transactional Outbox Pattern
To ensure atomic database updates and Kafka event emissions:
1. When domain services mutate state, they synchronously insert an event into the `outbox_events` table within the same `@Transactional` boundary using `OrderSagaOrchestrator.saveStateAndEvent()`.
2. A scheduled `OutboxEventPoller` polls this table.
3. It publishes events to Kafka (`order-events`) and deletes the row upon success.

## Saga Orchestration & Financial Ledger
1. **Order Creation**: Order is saved (Status: `PENDING_PAYMENT`). Outbox event (`ORDER_CREATED`) is emitted.
2. **Payment Integration**: `WebhookController` handles callbacks. Saga receives `PAYMENT_SUCCESS`.
3. **Logistics Dispatch**: Emits events to Delivery app. Wait for `DRIVER_ASSIGNED`. If `DISPATCH_FAILED`, triggers `DELIVERY_FAILED` and refund.
4. **Kitchen Acceptance**: Waits for `ORDER_ACCEPTED` from Restaurant app. If `ORDER_REJECTED` or `ORDER_CANCELLED_BY_RESTAURANT`, triggers `CANCELLED_BY_RESTAURANT` and refund. If delay requires approval, waits for `ORDER_DELAY_APPROVED` or `ORDER_DELAY_REJECTED` (which transitions to `CANCELLED`).
5. **Logistics Completion**: Waits for `ORDER_DELIVERED` from Delivery app.
6. **Ledger**: The `LedgerService` uses optimistic locking (`@Version`) to safely credit and debit the platform/restaurant/driver accounts asynchronously once delivered.

For visual diagrams, see `Deployment/flow_diagram.md` and `README.md` in the repository root.
