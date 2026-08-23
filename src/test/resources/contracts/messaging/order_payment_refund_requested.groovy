package contracts.messaging

/*
 * Mirrors the real wire payload for order-events / PAYMENT_REFUND_REQUESTED.
 * Produced by CustomerApplication OrderRefundService, which writes the outbox row with
 * AggregateType.PAYMENT -- OutboxProcessor routes that to payment-events, not order-events.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish the serialized PAYMENT_REFUND_REQUESTED event to order-events")
    label("order_payment_refund_requested")
    input {
        triggeredBy('firePaymentRefundRequested()')
    }
    outputMessage {
        sentTo('payment-events')
        headers {
            header('eventType', 'PAYMENT_REFUND_REQUESTED')
            header('aggregateType', 'PAYMENT')
        }
        body([
            intentId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            gatewayOrderId: "pay_12345",
            amountInInr: 15.50,
            gatewayName: "STRIPE",
            orderId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            refundDestination: "GATEWAY"
        ])
    }
}
