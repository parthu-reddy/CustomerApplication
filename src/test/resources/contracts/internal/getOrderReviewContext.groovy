package contracts.internal

import org.springframework.cloud.contract.spec.Contract

/*
 * The eligibility call. ReviewsService cannot decide whether a customer may review anything without
 * this response, and it was the only integration point in the reviews path with no contract.
 *
 * Written against InternalOrderController.getOrderReviewContext and OrderReviewContextDto, not
 * invented: the envelope is ApiResponse.success(...), so {success, message, data}, and every field
 * below is one the consumer reads.
 *
 * `deliveryExecutiveId` is nullable in the DTO (no driver was ever assigned) but is populated here,
 * because a contract asserting null would pin the one case in which there is nothing to review.
 * There is deliberately no driver NAME: the orders table has no rider-name column.
 */
Contract.make {
    description("should return the review context for a delivered order")
    request {
        method 'GET'
        urlPath(value(
            consumer(regex('/api/v1/internal/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/review-context')),
            producer('/api/v1/internal/orders/6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02/review-context')))
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            success: true,
            data: [
                orderId: '6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02',
                customerId: '123e4567-e89b-12d3-a456-426614174000',
                customerName: 'Priya Raghavan',
                restaurantId: '123e4567-e89b-12d3-a456-426614174001',
                restaurantName: 'Anand Bhavan',
                deliveryExecutiveId: '123e4567-e89b-12d3-a456-426614174000',
                // Completion is read from here, never from OrderStatus -- that enum has no
                // DELIVERED value and HANDED_OVER is not "the food arrived".
                deliveryStatus: 'DELIVERED',
                // Start of the review window: an Instant, so ISO-8601 in UTC with its 'Z'. The
                // regex used to pin the offset-free form, which production never sent (JacksonConfig
                // appended a literal Z): TimezoneCorrectness_2026-09-25.
                deliveredAt: $(producer(regex('\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z')),
                               consumer('2026-09-11T10:15:30Z')),
                items: [
                    [
                        menuItemId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
                        name: 'Ghee Roast Dosa'
                    ]
                ]
            ]
        ])
    }
}
