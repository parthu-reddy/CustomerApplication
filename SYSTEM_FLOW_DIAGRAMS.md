# Customer Application - System Flow Diagrams

This document illustrates the event-driven architecture and sequence flows of the `CustomerApplication`, which acts as the core Order Management and Customer Gateway for the Food Delivery platform.

## High-Level Event Architecture

```mermaid
graph TD
    subgraph Customer Application
        API[Customer REST API]
        DB[(customer_db)]
        Saga[Order Saga Orchestrator]
        Outbox[Outbox Event Poller]
    end

    subgraph Kafka Message Broker
        T1(order-events)
        T2(payment-events)
        T3(notification-events)
    end

    subgraph External Microservices
        RestApp[Restaurant Application]
        DelApp[Delivery Executive Application]
        PayApp[Payment Gateway Integration]
        NotifApp[Communication Integration]
    end

    API -->|Create Order| DB
    API -->|Insert Outbox| DB
    Outbox -->|Polls| DB
    Outbox -->|Publishes ORDER_CREATED| T1
    
    T1 -->|Consumed by| RestApp
    RestApp -->|Publishes ORDER_ACCEPTED/REJECTED| T1
    
    T1 -->|Consumed by| Saga
    T1 -->|Consumed by| DelApp
    
    DelApp -->|Publishes DRIVER_ASSIGNED/DELIVERED| T1
    
    PayApp -->|Publishes PAYMENT_SUCCESS| T2
    T2 -->|Consumed by| Saga
    
    Saga -->|Publishes PUSH_NOTIFICATION| T3
    T3 -->|Consumed by| NotifApp
```

## Order Creation Sequence (Saga)

```mermaid
sequenceDiagram
    participant C as Customer
    participant API as Customer API
    participant DB as Customer DB
    participant Outbox as Outbox Poller
    participant K_Order as Kafka (order-events)
    participant Rest as Restaurant App

    C->>API: POST /api/v1/orders
    activate API
    API->>DB: Save Order (Status: PENDING_PAYMENT)
    API->>DB: Save OutboxEvent (ORDER_CREATED)
    API-->>C: Order ID
    deactivate API

    Outbox->>DB: Poll Unprocessed Events
    Outbox->>K_Order: Publish ORDER_CREATED
    Outbox->>DB: Mark Processed

    K_Order->>Rest: Consume ORDER_CREATED
    Rest->>Rest: Notify Restaurant Dashboard
```

## Order Fulfillment & Dispatch Sequence

```mermaid
sequenceDiagram
    participant K_Order as Kafka (order-events)
    participant Saga as OrderSagaOrchestrator
    participant DB as Customer DB
    participant Del as Delivery App
    participant Notif as Communication App

    note over K_Order,Saga: Restaurant Accepts Order
    K_Order->>Saga: Consume ORDER_ACCEPTED
    Saga->>DB: Update Status to ACCEPTED
    
    K_Order->>Del: Consume ORDER_ACCEPTED
    Del->>Del: Dispatch LogisticsService (Find Driver)
    Del->>K_Order: Publish DRIVER_ASSIGNED
    
    K_Order->>Saga: Consume DRIVER_ASSIGNED
    Saga->>DB: Update Status to DISPATCHED
    Saga->>DB: Set DeliveryExecutiveId
    Saga->>Notif: Publish Notification (DRIVER_ON_THE_WAY)
    
    note over K_Order,Saga: Delivery Completed
    Del->>K_Order: Publish ORDER_DELIVERED
    K_Order->>Saga: Consume ORDER_DELIVERED
    Saga->>DB: Update Status to DELIVERED
    Saga->>DB: Calculate Ledger Splits (80% Rest, 20% Plat, Driver Payout)
```
