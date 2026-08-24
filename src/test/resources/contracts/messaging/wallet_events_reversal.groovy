package contracts.messaging

/*
 * wallet-events shape 2 of 2: FLAT, from AdminOrderManualController:225-245.
 *
 * REVERSAL_GENERATED is a debit against the restaurant or driver at fault for a post-delivery
 * refund. It is the opposite direction to REFUND_GENERATED, the credit to the customer's wallet
 * published by PaymentEventConsumer as shape 1 (an {eventType, payload} envelope).
 *
 * The type appears on the Kafka header (from the outbox row) and in the body, saying the same
 * thing -- the platform convention, shared with 28 other producers. Until 2026-08-24 the two
 * disagreed here: the row said REFUND_GENERATED because EventType had no REVERSAL_GENERATED
 * constant, while the body said REVERSAL_GENERATED. Consumers then had to pick the resolver with
 * the right precedence to get the direction of the money right. OutboxProcessor now rejects any
 * event whose row and payload types disagree. See ADR 002.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish flat REVERSAL_GENERATED to wallet-events")
    label("wallet_events_reversal")
    input { triggeredBy('fireWalletReversal()') }
    outputMessage {
        sentTo('wallet-events')
        headers { header('eventType', 'REVERSAL_GENERATED') }
        body([
            entityId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            entityType: "RESTAURANT",
            amount: "40.00",
            referenceId: $(producer(regex('REV_.+'))),
            description: $(producer(regex('.+'))),
            chargeCategory: "REFUND",
            eventType: "REVERSAL_GENERATED"
        ])
    }
}
