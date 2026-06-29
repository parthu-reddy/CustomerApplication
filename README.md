# Customer Application

The Customer Application acts as the core gateway and order state machine for the Food Delivery platform. It provides the customer-facing REST APIs and hosts the **Order Saga Orchestrator**.

## Responsibilities

1. **Customer Management**: Registration, profile handling.
2. **Order Intake**: Receiving orders, validating cart totals, and generating initial `PENDING` order states.
3. **Outbox Pattern**: Reliable publishing of initial `ORDER_CREATED` events via the transactional outbox table (`outbox_events`).
4. **Saga Orchestrator**: The `OrderSagaOrchestrator` manages the distributed transaction of a food delivery order. It listens to Kafka events from the Restaurant, Payment, and Delivery microservices to progress the order through its state machine and calculates financial ledger splits upon delivery.

## Database Schema (customer_db)

- `customers`: Profiles and auth.
- `orders`, `order_items`: Core commerce tables.
- `outbox_events`: Polled by a scheduler to guarantee at-least-once delivery to Kafka.
- `payment_intents`, `refunds`: Local materialized view of payment state to drive the saga.
- `ledgers`, `ledger_accounts`, `ledger_entries`: Double-entry accounting system to track platform fees, restaurant payouts, and delivery executive earnings upon successful order completion.

## Order Saga Sequence

```mermaid
sequenceDiagram
    participant C as Customer API
    participant Outbox as Outbox Poller
    participant K as Kafka
    participant Rest as Restaurant App
    participant Pay as Payment Gateway App
    participant Del as Delivery App

    C->>C: Create Order (Status: PENDING)
    C->>C: Save OutboxEvent
    Outbox->>K: Publish ORDER_CREATED
    
    K->>Rest: Consume ORDER_CREATED
    Rest->>K: Publish ORDER_ACCEPTED
    K->>C: Saga Consumes ORDER_ACCEPTED
    C->>C: Update Status: ACCEPTED
    
    K->>Pay: (Payment Intent Processed)
    Pay->>K: Publish PAYMENT_SUCCESS
    K->>C: Saga Consumes PAYMENT_SUCCESS
    C->>C: Update Status: PAID
    
    K->>Del: Consume ORDER_ACCEPTED
    Del->>K: Publish DRIVER_ASSIGNED
    K->>C: Saga Consumes DRIVER_ASSIGNED
    C->>C: Update Status: DISPATCHED
    
    Del->>K: Publish ORDER_DELIVERED
    K->>C: Saga Consumes ORDER_DELIVERED
    C->>C: Update Status: DELIVERED
    C->>C: Ledger Settlement (80% Rest, 20% Plat)
```

## Setup & Running

1. Ensure the PostgreSQL `customer_db` is running.
2. Run `mvn spring-boot:run`. Flyway will automatically apply migrations (`V1__init_schema.sql`).
