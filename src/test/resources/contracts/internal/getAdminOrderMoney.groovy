import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should return admin order money summary")
    request {
        method 'GET'
        urlPath(value(consumer(regex('/api/v1/internal/admin/orders/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/money')), producer('/api/v1/internal/admin/orders/123e4567-e89b-12d3-a456-426614174000/money')))
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            orderId: '123e4567-e89b-12d3-a456-426614174000',
            totalAmount: 100.0,
            foodCost: 50.0,
            restaurantPayout: 40.0,
            driverGrossPayout: 25.0,
            driverNetPayout: 20.0,
            customerPlatformFee: 10.0,
            restaurantPlatformFee: 5.0,
            sgst: 9.0,
            cgst: 9.0,
            deliveryFee: 20.0
        ])
    }
}
