---
name: understand-customer-service
description: Architectural overview and troubleshooting guide for the Customer Application. Use this skill when modifying the order lifecycle or investigating inter-service communication issues.
---

# Understand CustomerService

The CustomerService aggregates data from multiple domains to provide a unified experience for the user. It is highly dependent on synchronous calls to the `RestaurantApplication` and `PaymentGatewayIntegration`.

## Architecture & Integration

- **Synchronous Feign Calls**: During order creation, it synchronously calls `RestaurantApplication` to validate item availability and pricing, and `PaymentGatewayIntegration` to generate the payment intent.
- **Asynchronous Events**: Once a payment is successful, it listens to `payment-events` via Kafka to asynchronously transition the order state.
- **WebSocket Streaming**: For live tracking, it subscribes to `location-updates` published by the `DeliveryExecutiveApplication` and streams them to the connected client session.

## Troubleshooting

- **Order Creation Fails**: Since order creation relies on synchronous Feign calls, ensure `RestaurantApplication` and `PaymentGatewayIntegration` are healthy. Check for Feign `RetryableExceptions`.
- **Order Stuck in PENDING_PAYMENT**: Verify that the Kafka consumer for `payment-events` is active and successfully processing messages.
- **Tracking Not Updating**: Ensure the WebSocket connection is established and the `location-updates` Kafka topic has recent messages for the specific `orderId`.
