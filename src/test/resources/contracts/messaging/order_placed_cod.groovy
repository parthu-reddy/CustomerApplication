package contracts.messaging

/*
 * order-events: a cash order reaching the kitchen.
 *
 * Emitted by OrderActionService.emitOrderPlacedEvent from a real OrderPaidEvent, and consumed by
 * RestaurantApplication's OrderEventConsumer.handleOrderPaid, which creates the restaurant-side
 * order from it -- reading eleven of these fields.
 *
 * Added 2026-09-12. order-events carried contracts for ORDER_CREATED, ORDER_STATUS_UPDATED and
 * DISPATCH_CANDIDATE_FOUND only. The consumer therefore passed the shape audit on the strength of a
 * contract for a DIFFERENT event it also handles, while the event that actually opens a cash order
 * in the kitchen had no contract at all.
 *
 * paymentMethod is pinned to COD: the same emitter publishes ORDER_PAID for prepaid orders, and the
 * restaurant's handling of "collect cash on delivery" depends on telling them apart.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish ORDER_PLACED_COD to order-events when a cash order is placed")
    label("order_placed_cod")
    input { triggeredBy('fireOrderPlacedCod()') }
    outputMessage {
        sentTo('order-events')
        headers { header('eventType', 'ORDER_PLACED_COD') }
        body([
            orderId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            restaurantId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            customerId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            customerName: $(producer(regex('.+'))),
            paymentMethod: "COD",
            estimatedPrepTimeMinutes: 15,
            deliveryLat: 12.971598,
            deliveryLng: 77.594562,
            deliveryAddress: $(producer(regex('.+'))),
            itemsJson: $(producer(regex('\\[.*\\]'))),
            pickupOtp: $(producer(regex('[0-9]{4}'))),
            deliveryOtp: $(producer(regex('[0-9]{4}'))),
            totalAmount: 15.50,
            itemTotal: 12.00,
            restaurantPlatformFee: 1.20,
            restaurantDeliveryContribution: 0.80,
            platformBonus: 0.00,
            restaurantPayout: 10.00
        ])
    }
}
