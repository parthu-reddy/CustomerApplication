---
name: connect-to-backend
description: Explains how frontend applications or other AI agents should connect to the Food Delivery microservices APIs and WebSockets. Covers service URLs, response format, and required headers.
---

# Connecting to the Backend (Microservices Architecture)

This guide outlines how to interact with the Food Delivery backend from client applications. The backend is split into independent microservices, each running on its own port.

## Service Ports

| Service | Default Port | Database |
|---|---|---|
| Customer Application | `8080` | `customer_db` |
| Delivery Executive Application | `8082` | `delivery_db` |
| Restaurant Application | `8083` | `restaurant_db` |
| Payment Gateway Integration | `8084` | `payment_db` |
| Communication Integration | `8085` | `notification_db` |
| Maps Integration | `8086` | (PostGIS) |

## Key Endpoint Summary

### Customer App (`localhost:8080`)
- **Auth**: `POST /api/v1/auth/initiate`, `POST /api/v1/auth/verify`
- **Places**: `GET /api/v1/places/autocomplete`, `GET /api/v1/places/reverse-geocode`
- **Addresses**: `POST /api/v1/customers/{id}/addresses`, `GET /api/v1/customers/{id}/addresses`
- **Restaurants**: `GET /api/v1/restaurants/nearby`, `GET /api/v1/restaurants/{id}/delivery-availability`
- **Orders**: `POST /api/v1/orders`, `POST /api/v1/orders/{id}/delay-approval?approved=true|false`
- **Live Tracking**: `GET /api/v1/orders/{id}/live-tracking` (SSE stream)

### Restaurant App (`localhost:8083`)
- **Onboarding**: `POST /api/v1/brands`, `POST /api/v1/brands/{id}/outlets`
- **Catalog**: `POST /api/v1/brands/{id}/master-menu`, `GET /api/v1/restaurants/{id}/catalog/items`, `GET /api/v1/restaurants/{id}/menu/batch`
- **Fulfillment**:
  - `POST /api/v1/restaurants/{id}/fulfillment/orders/{orderId}/accept`
  - `POST /api/v1/restaurants/{id}/fulfillment/orders/{orderId}/reject`
  - `POST /api/v1/restaurants/{id}/fulfillment/orders/{orderId}/ready`
  - `POST /api/v1/restaurants/{id}/fulfillment/orders/{orderId}/cancel`

### Delivery App (`localhost:8082`)
- **Fleet**: `POST /api/delivery/onboard`, `POST /api/delivery/status`
- **Order Interaction**:
  - `POST /api/delivery/drivers/{driverId}/orders/{orderId}/accept`
  - `POST /api/delivery/drivers/{driverId}/orders/{orderId}/reject`
  - `POST /api/delivery/drivers/{driverId}/orders/{orderId}/status`
  - `POST /api/delivery/drivers/{driverId}/orders/{orderId}/timeout`
- **Routing**: `GET /api/v1/logistics/route`
- **Telemetry**: `POST /api/v1/delivery/telemetry/batch`

### Payment Service (`localhost:8084`)
- `POST /api/v1/payments/create-order`
- `POST /api/v1/payments/refund`
- **Webhooks** (called by gateways, not by clients):
  - `POST /api/v1/webhooks/razorpay`
  - `POST /api/v1/webhooks/cashfree`
  - `POST /api/v1/webhooks/vyapar`

### Maps Integration (`localhost:8086`)
- **Places**: `GET /api/places/autocomplete`, `GET /api/places/reverse-geocode`
- **Dispatch**: `POST /api/logistics/dispatch`
- **Routing**: `GET /api/logistics/route`
- **Fleet**: `POST /api/fleet/availability`, `GET /api/fleet/nearby`, `GET /api/fleet/location`, `GET /api/fleet/availability/check`

## Standard API Response

All endpoints return the `ApiResponse<T>` wrapper:
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "timestamp": "2024-05-20T10:15:30"
}
```

## Real-Time WebSocket Connections

### Driver Telemetry
- **DeliveryApp**: `ws://localhost:8082/tracking`
- **MapsIntegration**: `ws://localhost:8086/tracking`

**Payload (JSON)** — send every 3-5 seconds:
```json
{
  "driverId": "uuid-of-driver",
  "lat": 12.9715987,
  "lng": 77.5945627
}
```

### Customer Live Tracking (SSE)
- `GET http://localhost:8080/api/v1/orders/{orderId}/live-tracking`
- Returns `text/event-stream` with periodic driver location updates.

## Required HTTP Headers

| Header | Required On | Purpose |
|---|---|---|
| `Idempotency-Key` (UUID) | All POST/PUT requests | Prevents duplicate operations via Redis distributed lock |
| `X-Vyapar-Signature` | Vyapar webhook calls | HMAC-SHA256 signature verification |
| `Content-Type: application/json` | All request bodies | Standard JSON content type |

## Fault Tolerance
- **Idempotency**: `IdempotencyFilter` (from CommonLibrary) enforces deduplication on all POST/PUT calls.
- **Rate Limiting**: `RateLimitingService` (Bucket4j + Redis) applied globally.
- **Circuit Breakers**: Resilience4j protects outbound calls (Maps API, Payment gateways).
