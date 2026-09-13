package contracts.messaging

/*
 * wallet-events: the store-credit refund request.
 *
 * Published by RefundService.enqueueWalletCredit and consumed by WalletService's
 * RefundCreditConsumer, which credits the customer and reports completion back as PAYMENT_REFUNDED.
 *
 * Added 2026-09-12 to close a real gap. wallet-events previously carried contracts for
 * PAYMENT_REFUNDED and LEDGER_TRANSACTION_REQUEST only, so audit_consumer_contract_shapes compared
 * RefundCreditConsumer against events it never handles and reported a MISMATCH on the money path --
 * naming four fields as "absent" that the producer has always sent. A gate that cries wolf where
 * money moves is worse than no gate.
 *
 * Every field below is one the consumer actually reads. refundId in particular is the wallet's own
 * idempotency reference: it is what stops a redelivery crediting the same refund twice.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish WALLET_CREDIT_REQUESTED to wallet-events for a store-credit refund")
    label("wallet_events_credit_requested")
    input { triggeredBy('fireWalletCreditRequested()') }
    outputMessage {
        sentTo('wallet-events')
        headers { header('eventType', 'WALLET_CREDIT_REQUESTED') }
        body([
            eventType: "WALLET_CREDIT_REQUESTED",
            refundId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            orderId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            gatewayOrderId: $(producer(regex('.+'))),
            customerId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            amount: "15.50"
        ])
    }
}
