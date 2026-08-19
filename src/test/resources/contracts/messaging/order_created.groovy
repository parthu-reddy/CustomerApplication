package contracts.messaging

/*
 * Mirrors the real wire payload for order-events / ORDER_CREATED.
 *
 * OrderSagaOrchestrator persists an OutboxEventEntity whose payload is
 * objectMapper.writeValueAsString(OrderCreatedEvent), and OutboxProcessor publishes it with
 * kafkaTemplate.send(TOPIC_ORDER_EVENTS, aggregateId, payload). The body below is therefore the
 * flat serialization of com.fooddelivery.common.event.OrderCreatedEvent -- NOT an
 * {eventId, type, payload} envelope, which no producer in this system emits.
 *
 * Note there is deliberately no eventType field: OutboxProcessor transmits neither an eventType
 * header nor an eventType body field today. Adding that header is Phase 7; when it lands, this
 * contract gains a headers block and consumers stop depending on the JSON-body fallback.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish the serialized OrderCreatedEvent to order-events")
    label("order_created")
    input {
        triggeredBy('fireOrderCreated()')
    }
    outputMessage {
        sentTo('order-events')
        headers {
            // Phase 7: OutboxProcessor now publishes the event type it has always stored.
            header('eventType', 'ORDER_CREATED')
            header('aggregateType', 'ORDER')
        }
        body([
            orderId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            customerId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            restaurantId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            totalAmount: 15.50,
            deliveryLat: 12.971598,
            deliveryLng: 77.594562,
            deliveryAddress: "221B Baker Street, Bangalore",
            pickupOtp: $(producer(regex('[0-9]{4}'))),
            deliveryOtp: $(producer(regex('[0-9]{4}')))
        ])
    }
}
