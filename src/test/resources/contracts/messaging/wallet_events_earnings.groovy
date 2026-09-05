package contracts.messaging

/*
 * wallet-events shape 1 of 2: FLAT, from OrderActionService.emitEarningsGeneratedEvent.
 * No body eventType -- the type travels only as the Kafka header OutboxProcessor now sets.
 * This shape was silently dropped by GenericWalletEventConsumer until the Phase 7 fix.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish flat LEDGER_TRANSACTION_REQUEST to wallet-events")
    label("wallet_events_earnings")
    input { triggeredBy('fireWalletEarnings()') }
    outputMessage {
        sentTo('wallet-events')
        headers { header('eventType', 'LEDGER_TRANSACTION_REQUEST') }
        body([
            entityId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            entityType: "RESTAURANT",
            amount: "125.50",
            referenceId: $(producer(regex('ORDER_.+'))),
            description: $(producer(regex('.+'))),
            metadata: "{}"
        ])
    }
}
