# E2E Testing Product Constraints

When writing or fixing E2E tests, you must adhere to the following product business rules:

## Rider Order Assignment Limit
- **Rule**: Once a rider is assigned an order, they can **never** get prompted with any other order until they finish their current delivery.
- **Why it matters**: If you reuse the same rider phone number across multiple test methods or setup steps without completing the previous delivery, the subsequent steps will fail because the rider will not receive any new order pings.
- **Action**: Always use unique, random riders for each test case or setup step. Pick a random rider from the valid dummy data pool (`7000000001` to `7000000030`) instead of hardcoding `TestConfig.RIDER_PHONE`.

## Rider Order Assignment
- **Strict Single-Order Assignment**: Once a rider is assigned an order, they can NEVER be prompted with any other order until they finish their current delivery.
