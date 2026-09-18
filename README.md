# CustomerApplication

The CustomerApplication is the backend service handling all consumer-facing operations. It allows users to browse restaurants, add items to their cart, create orders, and track order status.

## Setup & Build
1. Build the service: `mvn clean install`
2. Run the application: `mvn spring-boot:run`
3. Port: `8081`

## Key Responsibilities
- **Restaurant Discovery**: View available restaurants and menus.
- **Cart Management**: Add/Remove items from the shopping cart.
- **Order Lifecycle**: Create orders and integrate with the PaymentService.
- **Real-time Tracking**: Provides WebSocket endpoints for live order tracking.

