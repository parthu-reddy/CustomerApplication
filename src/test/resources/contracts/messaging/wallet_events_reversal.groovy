package contracts.messaging

/*
 * wallet-events shape 2 of 2: ENVELOPED, from AdminOrderManualController.
 *
 * The body eventType (REVERSAL_GENERATED -> debit) deliberately differs from the outbox/header
 * eventType (REFUND_GENERATED -> credit). Both are asserted here precisely because they disagree:
 * a consumer that trusts the header over the body would turn this debit into a credit.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish enveloped REVERSAL_GENERATED to wallet-events")
    label("wallet_events_reversal")
    input { triggeredBy('fireWalletReversal()') }
    outputMessage {
        sentTo('wallet-events')
        headers { header('eventType', 'REFUND_GENERATED') }
        body([
            eventType: "REVERSAL_GENERATED",
            payload: [
                entityId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
                entityType: "RESTAURANT",
                amount: "40.00",
                referenceId: $(producer(regex('REV_.+'))),
                description: $(producer(regex('.+'))),
                chargeCategory: "REFUND"
            ]
        ])
    }
}
