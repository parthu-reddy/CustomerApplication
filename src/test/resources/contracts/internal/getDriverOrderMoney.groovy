import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should return driver order money summary")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/money/driver/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/earnings')), producer('/api/v1/internal/money/driver/orders/123e4567-e89b-12d3-a456-426614174000/earnings')))
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            orderId: '123e4567-e89b-12d3-a456-426614174000',
            grossPayout: 25.0,
            taxes: 5.0,
            netPayout: 20.0,
            platformBonus: 5.0
        ])
    }
}
