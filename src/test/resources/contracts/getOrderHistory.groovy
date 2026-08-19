package contracts
import org.springframework.cloud.contract.spec.Contract
Contract.make {
    request {
        method 'GET'
        urlPath('/api/v1/internal/orders/driver/00000000-0000-0000-0000-000000000000/history') {
            queryParameters {
                parameter 'page', '0'
                parameter 'size', '10'
            }
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
            content: [],
            totalElements: 0
        ])
    }
}
