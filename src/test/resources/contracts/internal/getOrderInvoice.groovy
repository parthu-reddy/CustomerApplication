
import org.springframework.cloud.contract.spec.Contract

/*
 * Corrected 2026-08-20. This asserted {invoiceId, amount}. getOrderInvoice returns
 * ResponseEntity<OrderResponse>, which has no `invoiceId` field at all and names the money field
 * `totalAmount`. It could never have passed.
 *
 * Note this endpoint lives on com.fooddelivery.customer.controller.InternalOrderController -- a
 * SECOND class of that name in this service, distinct from com.fooddelivery.order.controller's, and
 * mapped to the same /api/v1/internal/orders base path.
 */
Contract.make {
    description("should return order invoice")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/invoice')), producer('/api/v1/internal/orders/123e4567-e89b-12d3-a456-426614174000/invoice')))
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            id: '123e4567-e89b-12d3-a456-426614174000',
            totalAmount: 100.0
        ])
    }
}
