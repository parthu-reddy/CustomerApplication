package contracts.messaging

/*
 * Real payload for chat-events, from ChatRefundProcessorService (CHAT_SESSION aggregate,
 * eventType CHAT_REFUND_QUOTE_RESPONSE, key = the originating chat aggregateId).
 * quoteAmount is a BigDecimal serialized as a JSON number.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish a refund quote response to chat-events")
    label("chat_events")
    input { triggeredBy('fireChatEvent()') }
    outputMessage {
        sentTo('chat-events')
        headers { header('eventType', 'CHAT_REFUND_QUOTE_RESPONSE') }
        body([
            quoteAmount: 125.50,
            refundType: "PARTIAL"
        ])
    }
}
