
import org.springframework.cloud.contract.spec.Contract

/*
 * Corrected 2026-08-20. This asserted status: 'DELIVERED', but OrderStatus has no such value --
 * CREATED, PENDING_ACCEPTANCE, AWAITING_DELAY_APPROVAL, ACCEPTED, PREPARING, READY_FOR_PICKUP,
 * HANDED_OVER, CANCELLED, CANCELLED_BY_RESTAURANT. 'DELIVERED' is a DeliveryStatus, and Order carries
 * BOTH fields; the contract conflated them. Asserting `deliveryStatus` is what it meant.
 */
Contract.make {
    description("should return order history for driver")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/orders/driver/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/history')), producer('/api/v1/internal/orders/driver/123e4567-e89b-12d3-a456-426614174000/history'))) {
            queryParameters {
                // The rider's day as [from, to) instants, computed in the rider's own zone by the UI.
                parameter 'from': value(consumer(regex('\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z')), producer('2023-01-01T00:00:00Z'))
                parameter 'to': value(consumer(regex('\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z')), producer('2023-01-02T00:00:00Z'))
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
                    deliveryStatus: 'DELIVERED'
                ]
            ],
            totalElements: 1
        ])
    }
}
