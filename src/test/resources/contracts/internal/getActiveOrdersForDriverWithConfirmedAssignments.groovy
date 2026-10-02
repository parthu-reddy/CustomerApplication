import org.springframework.cloud.contract.spec.Contract

/*
 * DeliveryExecutiveApplication owns assignment and sends the orders it holds for the driver. One the
 * driver accepted moments ago is returned before DRIVER_ASSIGNED reaches the customer service.
 */
Contract.make {
    description("should include orders the delivery service holds for the driver")
    priority 1
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/orders/driver/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/active')), producer('/api/v1/internal/orders/driver/123e4567-e89b-12d3-a456-426614174000/active'))) {
            queryParameters {
                parameter 'confirmedOrderIds': value(consumer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}')), producer('7f7af6a5-1d86-4f29-b4cc-36eefe69a74b'))
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
                    id: '7f7af6a5-1d86-4f29-b4cc-36eefe69a74b',
                    status: 'ACCEPTED'
                ]
            ],
            totalElements: 1
        ])
    }
}
