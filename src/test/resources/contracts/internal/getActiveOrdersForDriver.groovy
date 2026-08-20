
import org.springframework.cloud.contract.spec.Contract

/*
 * Corrected 2026-08-20. This asserted status: 'OUT_FOR_DELIVERY', but OrderStatus has no such value --
 * CREATED, PENDING_ACCEPTANCE, AWAITING_DELAY_APPROVAL, ACCEPTED, PREPARING, READY_FOR_PICKUP,
 * HANDED_OVER, CANCELLED, CANCELLED_BY_RESTAURANT. 'OUT_FOR_DELIVERY' is a DeliveryStatus, and Order carries
 * BOTH fields; the contract conflated them. Asserting `deliveryStatus` is what it meant.
 */
Contract.make {
    description("should return active orders for driver")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/orders/driver/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/active')), producer('/api/v1/internal/orders/driver/123e4567-e89b-12d3-a456-426614174000/active'))) {
            queryParameters {
                parameter 'page': value(consumer(regex('\\d+')), producer('0'))
                parameter 'size': value(consumer(regex('\\d+')), producer('10'))
            }
        }
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            content: [
                [
                    id: '123e4567-e89b-12d3-a456-426614174000',
                    deliveryStatus: 'OUT_FOR_DELIVERY'
                ]
            ],
            totalElements: 1
        ])
    }
}
