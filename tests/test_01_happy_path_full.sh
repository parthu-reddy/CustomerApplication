#!/bin/bash
set -e

echo "--- START: Happy Path Full Cycle ---"

echo "1a. Onboarding Brand..."
BRAND_USER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
BRAND_RESPONSE=$(curl -s -X POST -H "X-User-Id: $BRAND_USER_ID" -H "X-User-Roles: RESTAURANT" -H "Content-Type: application/json" -d '{"name": "Happy Burger", "gstin": "123456789012345", "pan": "ABCDE1234F", "bankAccountNumber": "1234567890", "ifscCode": "HDFC0001234"}' http://localhost:8094/api/v1/brands)
echo "BRAND_RESPONSE: $BRAND_RESPONSE"
BRAND_ID=$(echo $BRAND_RESPONSE | jq -r '.data.id')

echo "1b. Onboarding Outlet..."
OUTLET_RESPONSE=$(curl -s -X POST -H "X-User-Id: $BRAND_USER_ID" -H "X-User-Roles: RESTAURANT" -H "Content-Type: application/json" -d '{"name": "Happy Burger Outlet", "fssaiLicenseNumber": "12345678901234", "lat": 12.9716, "lng": 77.5946, "timings": [{"openingTime": "00:00:00", "closingTime": "23:59:59"}]}' http://localhost:8094/api/v1/brands/$BRAND_ID/outlets)
echo "OUTLET_RESPONSE: $OUTLET_RESPONSE"
REST_ID=$(echo $OUTLET_RESPONSE | jq -r '.data.id')

echo "2. Adding Master Menu Item..."
MENU_RESPONSE=$(curl -s -X POST -H "X-User-Id: $BRAND_USER_ID" -H "X-User-Roles: RESTAURANT" -H "Content-Type: application/json" -d '{"name": "Happy Meal", "description": "Good meal", "basePrice": 150.00}' http://localhost:8094/api/v1/brands/$BRAND_ID/master-menu)
echo "MENU_RESPONSE: $MENU_RESPONSE"
MENU_ID=$(echo $MENU_RESPONSE | jq -r '.data.id')

echo "3. Creating Customer in DB..."
CUST_PHONE="999$(printf "%07d" $RANDOM$RANDOM | cut -c1-7)"
CUST_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d food_delivery -c "INSERT INTO customers (id, phone_number) VALUES ('$CUST_ID', '$CUST_PHONE');" > /dev/null
echo "CUST_ID: $CUST_ID"

echo "3.5. Adding Customer Address..."
ADDR_RESPONSE=$(curl -s -X POST -H "X-User-Id: $CUST_ID" -H "X-User-Roles: CUSTOMER" -H "Content-Type: application/json" -d "{
  \"label\": \"Home\",
  \"addressLine1\": \"123 Main St\",
  \"city\": \"Bengaluru\",
  \"state\": \"Karnataka\",
  \"zipCode\": \"560001\",
  \"latitude\": 12.9716,
  \"longitude\": 77.5946
}" http://localhost:8092/api/v1/customers/$CUST_ID/addresses)
echo "ADDR_RESPONSE: $ADDR_RESPONSE"
ADDR_ID=$(echo $ADDR_RESPONSE | jq -r '.data.id')

echo "4. Onboarding Delivery Executive..."
DEL_PHONE="888$(printf "%07d" $RANDOM$RANDOM | cut -c1-7)"
DEL_EXEC_USER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DEL_RESPONSE=$(curl -s -X POST -H "X-User-Id: $DEL_EXEC_USER_ID" -H "X-User-Roles: DELIVERY" -H "Content-Type: application/json" -d "{\"fullName\": \"Happy Exec\", \"phoneNumber\": \"$DEL_PHONE\", \"vehicleNumber\": \"KA01AB1234\", \"photoUrl\": \"http://example.com/photo.jpg\"}" http://localhost:8095/api/delivery/onboard)
echo "DEL_RESPONSE: $DEL_RESPONSE"
DEL_EXEC_ID=$(echo $DEL_RESPONSE | jq -r '.data.id')

echo "4.5. Making Delivery Executive ONLINE and Setting Location..."
curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: DELIVERY" -H "Content-Type: application/json" -d "{\"driverId\": \"$DEL_EXEC_ID\", \"available\": true}" http://localhost:8095/api/delivery/status > /dev/null
curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: DELIVERY" -H "Content-Type: application/json" -d "[{\"driverId\": \"$DEL_EXEC_ID\", \"lat\": 12.9716, \"lng\": 77.5946, \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}]" http://localhost:8095/api/v1/delivery/telemetry/batch > /dev/null

echo "5. Placing Order..."
ORDER_RESPONSE=$(curl -s -X POST -H "X-User-Id: $CUST_ID" -H "X-User-Roles: CUSTOMER" -H "Content-Type: application/json" -d "{
  \"customerId\": \"$CUST_ID\",
  \"restaurantId\": \"$REST_ID\",
  \"deliveryAddressId\": \"$ADDR_ID\",
  \"items\": [{\"menuItemId\": \"$MENU_ID\", \"quantity\": 2}]
}" http://localhost:8092/api/v1/orders)
echo "ORDER_RESPONSE: $ORDER_RESPONSE"
ORDER_ID=$(echo $ORDER_RESPONSE | jq -r '.data.id')
PICKUP_OTP=$(echo $ORDER_RESPONSE | jq -r '.data.pickupOtp')
DELIVERY_OTP=$(echo $ORDER_RESPONSE | jq -r '.data.otp')
PAYMENT_INTENT=$(echo $ORDER_RESPONSE | jq -r '.data.paymentIntent')

echo "6. Simulating Payment Webhook..."
PAYLOAD="{\"event\": \"payment.success\",\"payload\": {\"payment\": {\"entity\": {\"order_id\": \"$ORDER_ID\",\"status\": \"captured\",\"amount\": 300.00}}}}"
SIG=$(python3 -c "import hmac, hashlib, base64; print(base64.b64encode(hmac.new(b'test_vyapar_webhook_secret', b'$PAYLOAD', hashlib.sha256).digest()).decode())")
curl -s -X POST -H "Content-Type: application/json" -H "X-VyaparGateway-Signature: $SIG" -d "$PAYLOAD" http://localhost:8085/api/v1/webhooks/vyapar > /dev/null

echo "7. Polling for PENDING_ACCEPTANCE Status..."
MAX_RETRIES=20
for (( i=1; i<=MAX_RETRIES; i++ )); do
  ORDER_STATUS=$(docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "PENDING_ACCEPTANCE" ] || [ "$ORDER_STATUS" == "ACCEPTED" ]; then break; fi
  sleep 2
done

if [ "$ORDER_STATUS" != "PENDING_ACCEPTANCE" ] && [ "$ORDER_STATUS" != "ACCEPTED" ]; then echo "FAIL: Expected PENDING_ACCEPTANCE or ACCEPTED, got $ORDER_STATUS"; exit 1; fi


echo "7.5. Polling for CREATED Status in Restaurant..."
for (( i=1; i<=MAX_RETRIES; i++ )); do
  REST_ORDER_STATUS=$(docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d restaurant_db -t -c "SELECT status FROM restaurant_orders WHERE order_id = '$ORDER_ID';" | xargs)
  if [ "$REST_ORDER_STATUS" == "CREATED" ] || [ "$REST_ORDER_STATUS" == "ACCEPTED" ]; then break; fi
  sleep 2
done
if [ "$REST_ORDER_STATUS" != "CREATED" ] && [ "$REST_ORDER_STATUS" != "ACCEPTED" ]; then echo "FAIL: Expected CREATED in restaurant_db, got $REST_ORDER_STATUS"; exit 1; fi


echo "8. Restaurant Accepts Order..."
curl -s -X POST -H "X-User-Id: $BRAND_USER_ID" -H "X-User-Roles: RESTAURANT" http://localhost:8094/api/v1/restaurants/$REST_ID/fulfillment/orders/$ORDER_ID/accept > /dev/null

echo "9. Polling for ACCEPTED Status (Restaurant Accepted)..."
for (( i=1; i<=MAX_RETRIES; i++ )); do
  ORDER_STATUS=$(docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "ACCEPTED" ] || [ "$ORDER_STATUS" == "PICKED_UP" ]; then break; fi
  sleep 2
done
if [ "$ORDER_STATUS" != "ACCEPTED" ] && [ "$ORDER_STATUS" != "PICKED_UP" ]; then echo "FAIL: Expected ACCEPTED, got $ORDER_STATUS"; exit 1; fi

echo "9.5. Restaurant marks order PREPARING..."
curl -s -X POST -H "X-User-Id: $BRAND_USER_ID" -H "X-User-Roles: RESTAURANT" http://localhost:8094/api/v1/restaurants/$REST_ID/fulfillment/orders/$ORDER_ID/prepare > /dev/null

echo "10. Restaurant marks order READY_FOR_PICKUP..."
curl -s -X POST -H "X-User-Id: $BRAND_USER_ID" -H "X-User-Roles: RESTAURANT" http://localhost:8094/api/v1/restaurants/$REST_ID/fulfillment/orders/$ORDER_ID/ready > /dev/null

echo "11. Polling for READY_FOR_PICKUP Status..."
for (( i=1; i<=MAX_RETRIES; i++ )); do
  ORDER_STATUS=$(docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "READY_FOR_PICKUP" ] || [ "$ORDER_STATUS" == "PICKED_UP" ]; then break; fi
  sleep 2
done
if [ "$ORDER_STATUS" != "READY_FOR_PICKUP" ] && [ "$ORDER_STATUS" != "PICKED_UP" ]; then echo "FAIL: Expected READY_FOR_PICKUP, got $ORDER_STATUS"; exit 1; fi

echo "12. Driver accepts ping (Driver Assigned)..."
curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: DELIVERY" "http://localhost:8095/api/delivery/drivers/$DEL_EXEC_ID/orders/$ORDER_ID/accept" > /dev/null

echo "12.2. Driver marks AT_RESTAURANT..."
curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: DELIVERY" -H "Content-Type: application/json" -d "{\"status\": \"AT_RESTAURANT\"}" "http://localhost:8095/api/delivery/drivers/$DEL_EXEC_ID/orders/$ORDER_ID/status" > /dev/null

echo "12.5. Driver picks up order (OUT_FOR_DELIVERY)..."
curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: DELIVERY" -H "Content-Type: application/json" -d "{\"status\": \"OUT_FOR_DELIVERY\", \"pickupOtp\": \"$PICKUP_OTP\"}" "http://localhost:8095/api/delivery/drivers/$DEL_EXEC_ID/orders/$ORDER_ID/status" > /dev/null

echo "13. Polling for PICKED_UP Status..."
for (( i=1; i<=MAX_RETRIES; i++ )); do
  ORDER_STATUS=$(docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "PICKED_UP" ]; then break; fi
  sleep 2
done
if [ "$ORDER_STATUS" != "PICKED_UP" ]; then echo "FAIL: Expected PICKED_UP, got $ORDER_STATUS"; exit 1; fi

echo "14. Driver marks DELIVERED..."
curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: DELIVERY" -H "Content-Type: application/json" -d "{\"status\": \"DELIVERED\", \"deliveryOtp\": \"$DELIVERY_OTP\"}" "http://localhost:8095/api/delivery/drivers/$DEL_EXEC_ID/orders/$ORDER_ID/status" > /dev/null

echo "15. Polling for DELIVERED Status..."
for (( i=1; i<=MAX_RETRIES; i++ )); do
  ORDER_STATUS=$(docker exec -e PGPASSWORD=prod_secure_password_123 -i shared_postgres psql -h localhost -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "DELIVERED" ]; then break; fi
  sleep 2
done
if [ "$ORDER_STATUS" != "DELIVERED" ]; then echo "FAIL: Expected DELIVERED, got $ORDER_STATUS"; exit 1; fi

echo "--- SUCCESS: Happy Path Completed Successfully ---"
exit 0
