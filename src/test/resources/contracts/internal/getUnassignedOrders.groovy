
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should return unassigned orders")
    request {
        method 'GET'
        urlPath('/api/v1/internal/orders/unassigned')
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            [
                id: '123e4567-e89b-12d3-a456-426614174000',
                status: 'PREPARING'
            ]
        ])
    }
}
