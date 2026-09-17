import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should return restaurant order money summary")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/money/restaurant/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/earnings')), producer('/api/v1/internal/money/restaurant/orders/123e4567-e89b-12d3-a456-426614174000/earnings')))
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            orderId: '123e4567-e89b-12d3-a456-426614174000',
            foodCost: 50.0,
            netPayout: 40.0,
            platformFee: 5.0,
            deliveryContribution: 5.0
        ])
    }
}
