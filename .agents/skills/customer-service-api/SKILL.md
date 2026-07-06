---
name: customer-service-api
description: Complete API reference and integration guide for the Customer Application. Use this when building frontends, mobile apps, or agents that need to interact with auth, address management, restaurant discovery, order creation, delay approval, or live tracking.
---

# Customer Service API

The Customer Service provides the primary interface for consumer interactions.

## Base URL
External requests must go through the ApiGateway: `http://localhost:8080/api/customer`

## Core Endpoints

### 1. Restaurant Discovery
`GET /restaurants`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: List of available restaurants and basic metadata.

### 2. View Menu
`GET /restaurants/{id}/menu`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: Full catalog for the specified restaurant.

### 3. Order Placement
`POST /orders`
- **Headers**: `Authorization: Bearer <token>`
- **Payload**:
  ```json
  {
    "restaurantId": "uuid",
    "items": [
      { "itemId": "uuid", "quantity": 2 }
    ],
    "deliveryAddressId": "uuid"
  }
  ```

### 4. Live Tracking (WebSocket)
`WS /ws/tracking`
- **Description**: Connect to this endpoint to receive real-time location updates of the delivery executive. Events are consumed from the Kafka `location-updates` topic.
