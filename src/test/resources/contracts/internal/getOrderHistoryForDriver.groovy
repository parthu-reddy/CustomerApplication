
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should return order history for driver")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/orders/driver/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/history')), producer('/api/v1/internal/orders/driver/123e4567-e89b-12d3-a456-426614174000/history'))) {
            queryParameters {
                parameter 'date': value(consumer(regex('.*')), producer('2023-01-01'))
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
                    status: 'DELIVERED'
                ]
            ],
            totalElements: 1
        ])
    }
}
