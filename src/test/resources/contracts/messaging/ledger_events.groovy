package contracts.messaging

org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish a ledger transaction request to ledger-events")
    label("ledger_events")
    input { triggeredBy('fireLedgerEvent()') }
    outputMessage {
        sentTo('ledger-events')
        headers { header('eventType', 'LEDGER_TRANSACTION_REQUEST') }
        body([
            transactionId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            referenceId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            producer: "customer-application",
            legs: [
                [
                    fromId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
                    fromType: "PLATFORM_CLEARING",
                    toId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
                    toType: "RESTAURANT_PAYABLE",
                    amount: 125.50,
                    category: "FOOD_COST"
                ]
            ]
        ])
    }
}
