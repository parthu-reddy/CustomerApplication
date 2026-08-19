
import org.springframework.cloud.contract.spec.Contract

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
                    status: 'OUT_FOR_DELIVERY'
                ]
            ],
            totalElements: 1
        ])
    }
}
