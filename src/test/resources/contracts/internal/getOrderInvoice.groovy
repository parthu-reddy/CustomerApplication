
import org.springframework.cloud.contract.spec.Contract

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
            invoiceId: 'INV-123',
            amount: 100.0
        ])
    }
}
