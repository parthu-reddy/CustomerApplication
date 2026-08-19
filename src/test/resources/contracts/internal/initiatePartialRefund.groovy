
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should initiate partial refund")
    request {
        method 'POST'
        urlPath(value(consumer(regex('/api/v1/internal/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/partial-refund')), producer('/api/v1/internal/orders/123e4567-e89b-12d3-a456-426614174000/partial-refund')))
        headers {
            contentType applicationJson()
        }
        body([
            reason: 'Item missing'
        ])
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            status: 'REFUND_INITIATED'
        ])
    }
}
