# CustomerApplication Architecture

The CustomerApplication acts as an orchestrator for the customer journey, relying on other domain services (Restaurant, Payment) via Feign clients and event streams.

## Detailed Sequence Diagram

```mermaid
sequenceDiagram
    participant CustomerClient as Mobile/Web App
    participant ApiGateway
    participant CustomerApp as CustomerApplication
    participant RestaurantApp as RestaurantApplication
    participant PaymentApp as PaymentGatewayIntegration
    participant Kafka

    %% Order Placement Flow
    CustomerClient->>ApiGateway: POST /api/customer/orders (JWT)
    ApiGateway->>CustomerApp: POST /api/customer/orders (X-User-Id)
    
    CustomerApp->>RestaurantApp: Feign: GET /api/v1/internal/restaurants/catalog
    RestaurantApp-->>CustomerApp: Catalog Data
    
    CustomerApp->>PaymentApp: Feign: POST /api/v1/internal/payments/create-order
    PaymentApp-->>CustomerApp: PaymentGateway Order ID
    
    CustomerApp->>CustomerApp: Save Order (Status: PENDING_PAYMENT)
    CustomerApp-->>ApiGateway: Return Order Details & Payment Link
    ApiGateway-->>CustomerClient: 200 OK
    
    %% Post Payment Flow (Async)
    Kafka->>CustomerApp: Consume PaymentEvent (SUCCESS)
    CustomerApp->>CustomerApp: Update Order (Status: CONFIRMED)
    CustomerApp->>Kafka: Publish OrderEvent (NEW_ORDER)
```
