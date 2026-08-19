package contracts.messaging

/*
 * Real payload for platform.notifications.dispatch: a serialized NotificationRequestEvent
 * (OrderSagaOrchestrator / OrderActionService, NOTIFICATION aggregate, key = customerId).
 * Note PaymentEventConsumer publishes a DIFFERENT {template, data} shape to this same topic for
 * refund notifications -- that variant is not yet contracted.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should publish a NotificationRequestEvent to platform.notifications.dispatch")
    label("notification_dispatch")
    input { triggeredBy('fireNotificationDispatch()') }
    outputMessage {
        sentTo('platform.notifications.dispatch')
        headers { header('eventType', 'NOTIFICATION_REQUEST') }
        body([
            eventId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            userId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            channel: "PUSH",
            eventName: "ORDER_CONFIRMED",
            templateParams: ["3f2504e0-4f89-41d3-9a0c-0305e82c3301"]
        ])
    }
}
