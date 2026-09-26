package contracts
import org.springframework.cloud.contract.spec.Contract
Contract.make {
    request {
        method 'GET'
        urlPath('/api/v1/internal/orders/driver/00000000-0000-0000-0000-000000000000/history') {
            queryParameters {
                // The rider's day as [from, to) instants (TimezoneCorrectness_2026-09-25): every caller sends one.
                parameter 'from': value(consumer(regex('\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z')), producer('2023-01-01T00:00:00Z'))
                parameter 'to': value(consumer(regex('\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z')), producer('2023-01-02T00:00:00Z'))
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
