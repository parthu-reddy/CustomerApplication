package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Should return unassigned orders")
    request {
        method GET()
        url("/api/v1/internal/orders/unassigned")
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([])
    }
}
