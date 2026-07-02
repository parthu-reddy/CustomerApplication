---
name: customer-service-api
description: Complete API reference and integration guide for the Customer Application. Use this when building frontends, mobile apps, or agents that need to interact with auth, address management, restaurant discovery, order creation, delay approval, or live tracking.
---

# Customer Application: API & Integration Guide

This is the central order management and customer gateway service for the Food Delivery platform. It acts as the **Saga Orchestrator** — coordinating payment, kitchen, and delivery workflows across all microservices. It runs as an independent Spring Boot microservice on **port 8080** (default).

## Base URL
Default local environment: `http://localhost:8080`

## REST API Endpoints

### Authentication

#### 1. Initiate Login (OTP)
**Endpoint**: `POST /api/v1/auth/initiate`
**Purpose**: Send an OTP to the customer's phone number.
**Body (JSON)**:
```json
{
  "phoneNumber": "9876543210"
}
```
**Side Effects**: Stores OTP in Redis with 5-minute TTL. Dispatches SMS notification via `NotificationRouterService`.

#### 2. Verify OTP
**Endpoint**: `POST /api/v1/auth/verify`
**Purpose**: Verify the OTP and return a JWT token. Creates a new customer record if first login.
**Body (JSON)**:
```json
{
  "phoneNumber": "9876543210",
  "otp": "123456"
}
```
**Response**: Returns a JWT token string.

---

### Address Management

#### 3. Add Address
**Endpoint**: `POST /api/v1/customers/{customerId}/addresses`
**Purpose**: Save a delivery address for the customer.
**Body (JSON)**:
```json
{
  "label": "Home",
  "fullAddress": "123 Main St, Koramangala",
  "latitude": 12.9352,
  "longitude": 77.6245,
  "city": "Bangalore"
}
```

#### 4. List Addresses
**Endpoint**: `GET /api/v1/customers/{customerId}/addresses`
**Purpose**: Retrieve all saved addresses for the customer.

---

### Restaurant Discovery

#### 5. Nearby Restaurants
**Endpoint**: `GET /api/v1/restaurants/nearby`
**Purpose**: Fetch restaurants within the customer's delivery radius.
**Query Parameters**:
- `lat` (Double, required): Customer's latitude
- `lng` (Double, required): Customer's longitude
- `radius` (Double, optional): Search radius in km (default: 5.0)

#### 6. Check Delivery Availability
**Endpoint**: `GET /api/v1/restaurants/{id}/delivery-availability`
**Purpose**: Check if delivery drivers are available near a specific restaurant before placing an order.
**Query Parameters**:
- `lat` (Double, required): Customer's delivery latitude
- `lng` (Double, required): Customer's delivery longitude

---

### Order Management

#### 7. Create Order
**Endpoint**: `POST /api/v1/orders`
**Purpose**: Place a new food order. This is the entry point for the entire Saga.
**Body (JSON)**:
```json
{
  "customerId": "uuid-of-customer",
  "restaurantId": "uuid-of-restaurant",
  "deliveryAddressId": "uuid-of-address",
  "items": [
    {
      "menuItemId": "uuid-of-menu-item",
      "quantity": 2
    }
  ]
}
```
**Validation**:
- Fetches menu items and restaurant details from RestaurantApplication (REST).
- Checks driver availability via MapsIntegration (REST).
- Validates delivery address is within 5km of the restaurant.
- Computes order total from actual restaurant menu prices (prevents price tampering).

**Side Effects**:
1. Saves order with status `CREATED`.
2. Creates an `ORDER_CREATED` outbox event.
3. Requests a payment intent from PaymentGatewayIntegration (REST).

**Response**: Order summary with payment intent details.

#### 8. Approve/Reject Delay
**Endpoint**: `POST /api/v1/orders/{orderId}/delay-approval`
**Purpose**: Customer approves or rejects a delay requested by the restaurant.
**Query Parameters**:
- `approved` (Boolean, required): `true` to approve, `false` to reject.

**Side Effects**:
- If approved: Publishes `ORDER_DELAY_APPROVED` → restaurant starts prep.
- If rejected: Publishes `ORDER_DELAY_REJECTED` → triggers refund, notifies restaurant & delivery to stop.
- 10-minute timeout auto-rejects if customer doesn't respond.

---

### Live Tracking (SSE)

#### 9. Live Order Tracking
**Endpoint**: `GET /api/v1/orders/{orderId}/live-tracking`
**Content-Type**: `text/event-stream` (Server-Sent Events)
**Purpose**: Stream real-time driver location updates to the customer app.

---

### Places (Maps Proxy)

#### 10. Autocomplete
**Endpoint**: `GET /api/v1/places/autocomplete`
**Query Parameters**: `input` (String, required)

#### 11. Reverse Geocode
**Endpoint**: `GET /api/v1/places/reverse-geocode`
**Query Parameters**: `lat`, `lng` (Double, required)

---

## Kafka Integration

### Consumed Events
| Topic | Event | Action |
|---|---|---|
| `payment-events` | `PaymentCompletedEvent` | Transitions order to `PAID`, emits `ORDER_PAID` via outbox |
| `payment-events` | `PaymentFailedEvent` | Cancels order |
| `order-events` | `ORDER_ACCEPTED` | Transitions to `ACCEPTED` |
| `order-events` | `ORDER_REJECTED` | Transitions to `CANCELLED_BY_RESTAURANT`, triggers refund |
| `order-events` | `ORDER_CANCELLED_BY_RESTAURANT` | Transitions to `CANCELLED_BY_RESTAURANT`, triggers refund |
| `order-events` | `ORDER_DELAY_APPROVAL_REQUESTED` | Transitions to `AWAITING_DELAY_APPROVAL`, notifies customer |
| `order-events` | `ORDER_DELAY_REJECTED` | Transitions to `CANCELLED`, triggers refund |
| `order-events` | `DRIVER_ASSIGNED` | Transitions to `DISPATCHED` |
| `order-events` | `DISPATCH_FAILED` | Transitions to `DELIVERY_FAILED`, triggers refund |
| `order-events` | `ORDER_READY` | Transitions to `READY_FOR_PICKUP` |
| `order-events` | `ORDER_PICKED_UP` | Transitions to `OUT_FOR_DELIVERY` |
| `order-events` | `ORDER_DELIVERED` | Transitions to `DELIVERED`, processes ledger payouts |

### Published Events (via Outbox)
| Event | Published To |
|---|---|
| `ORDER_CREATED` | `order-events` |
| `ORDER_PAID` | `order-events` |
| `ORDER_DELAY_APPROVED` | `order-events` |
| `ORDER_DELAY_REJECTED` | `order-events` |
| `NotificationRequestEvent` | `platform.notifications.dispatch` |

## State Machine
Orders use the State Pattern (`OrderState` interface) with implementations:
- `CreatedState` → `PaidState` → `AcceptedState` → `DispatchedState` → `DeliveredState`
- `PaidState` → `AwaitingDelayApprovalState` → back to `PaidState` (approved) or `TerminalState` (rejected)
- Any state → `TerminalState` (for cancellations, rejections, delivery failures)
- `TerminalState` handles: `CANCELLED`, `CANCELLED_BY_RESTAURANT`, `DELIVERY_FAILED`, `CANCELLED_AND_REFUNDED`

## Database
- **PostgreSQL** database: `customer_db`
- **Flyway migrations**: `src/main/resources/db/migration/` (V1–V7)
- Key tables: `customers`, `customer_addresses`, `orders`, `order_items`, `outbox_events`, `ledger_entries`

## Financial Ledger
On `ORDER_DELIVERED`, the `DoubleEntryLedgerService` processes:
- **Restaurant Payout**: 80% of `totalAmount`
- **Driver Payout**: Flat delivery fee
- **Platform Revenue**: Remainder
Uses optimistic locking (`@Version`) for concurrent safety.
