package contracts.messaging

/*
 * Real payload for ledger-events, from OrderActionService (LEDGER aggregate,
 * eventType LEDGER_TRANSACTION_REQUEST, key = transferId). Flat ObjectNode.
 * NOTE: amount is a STRING (amount.toString()), not a JSON number.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish a ledger transaction request to ledger-events")
    label("ledger_events")
    input { triggeredBy('fireLedgerEvent()') }
    outputMessage {
        sentTo('ledger-events')
        headers { header('eventType', 'LEDGER_TRANSACTION_REQUEST') }
        body([
            transferId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            referenceId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            fromId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            fromType: "PLATFORM",
            toId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            toType: "RESTAURANT",
            amount: "125.50",
            chargeCategory: "FOOD_COST"
        ])
    }
}
