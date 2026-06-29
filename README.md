# Customer Application (Order Management Gateway)

The Customer Application is the core orchestration layer of the Food Delivery Platform. It serves as the primary entry point for customers to browse restaurants, place orders, and track their delivery status. Under the hood, it acts as a Saga Orchestrator, coordinating the entire order fulfillment lifecycle across multiple independent microservices via Kafka events.

## Key Responsibilities

1. **Order Management & Orchestration**: 
   - Manages the central `orders` database table (`customer_db`).
   - Implements the Saga pattern via `OrderSagaOrchestrator` to handle distributed transactions.
2. **Customer Gateway**: 
   - Exposes REST APIs for customer clients (mobile/web) to place orders and view history.
3. **Outbox Pattern**:
   - Uses the Transactional Outbox pattern (`OutboxEventPoller`) to reliably publish events to Kafka without distributed transactions.
4. **Ledger & Accounting**:
   - Manages the `DoubleEntryLedgerService` to handle split payouts (Platform fee, Restaurant payout, Driver payout).

## Architecture & Integrations

- **Database**: PostgreSQL (`customer_db`). Fully isolated; no other service accesses this database.
- **Message Broker**: Apache Kafka.
- **Events Published**: 
  - `ORDER_CREATED` -> `order-events` (Consumed by Restaurant)
  - `PUSH_NOTIFICATION` -> `notification-events` (Consumed by CommunicationService)
- **Events Consumed**:
  - `ORDER_ACCEPTED`, `ORDER_REJECTED`, `DRIVER_ASSIGNED`, `ORDER_DELIVERED` (From `order-events`)
  - `PAYMENT_SUCCESS` (From `payment-events`)

## Running Locally

```bash
# Start required infrastructure (Kafka, Zookeeper, PostgreSQL)
docker-compose up -d

# Run the application
./mvnw spring-boot:run
```
