
import org.springframework.cloud.contract.spec.Contract

/*
 * Corrected 2026-08-20. Two errors, both fatal:
 *   - the request sent only `reason`, but partialRefund() requires `amount` and 400s without it.
 *     `reason` is not sent by the only production caller (RestaurantApplication's
 *     FulfillmentService builds {"amount": ...} alone), so requiring it here would make the
 *     contract stricter than reality;
 *   - the response asserted {status: 'REFUND_INITIATED'}, but the handler returns
 *     ApiResponse.success(...), i.e. {success, message, data}.
 */
Contract.make {
    description("should initiate partial refund")
    request {
        method 'POST'
        urlPath(value(consumer(regex('/api/v1/internal/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/partial-refund')), producer('/api/v1/internal/orders/123e4567-e89b-12d3-a456-426614174000/partial-refund')))
        headers {
            contentType applicationJson()
        }
        body([
            amount: '25.00'
        ])
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            success: true,
            data: 'Partial refund requested successfully'
        ])
    }
}
