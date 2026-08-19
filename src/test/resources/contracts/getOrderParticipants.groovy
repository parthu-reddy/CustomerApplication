package contracts
import org.springframework.cloud.contract.spec.Contract
Contract.make {
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/participants')), producer('/api/v1/internal/orders/123e4567-e89b-12d3-a456-426614174000/participants')))
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
            "123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174001"
        ])
    }
}
