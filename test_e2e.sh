#!/bin/bash
set -e

BRAND_OWNER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DEL_EXEC_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
CUST_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

echo "1. Creating Brand..."
RAND_NUM=$(printf "%04d" $((RANDOM % 10000)))
RAND_GSTIN="12345678901${RAND_NUM}"
RAND_PAN="ABCDE${RAND_NUM}F"
RAND_CIN="U12345MH2023PTC12${RAND_NUM}"
BRAND_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $BRAND_OWNER_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d "{\"name\": \"E2E Brand\", \"gstin\": \"$RAND_GSTIN\", \"pan\": \"$RAND_PAN\", \"cin\": \"$RAND_CIN\", \"bankAccountNumber\": \"1234567890\", \"ifscCode\": \"HDFC0001234\"}" http://localhost:8094/api/v1/brands)
echo "BRAND_RESPONSE: $BRAND_RESPONSE"
BRAND_ID=$(echo $BRAND_RESPONSE | jq -r '.data.id')

echo "1.5 Creating Outlet..."
REST_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $BRAND_OWNER_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d '{"name": "E2E Burger", "fssaiLicenseNumber": "12345678901234", "lat": 12.9716, "lng": 77.5946, "openingTime": "00:00:00", "closingTime": "23:59:59"}' http://localhost:8094/api/v1/brands/$BRAND_ID/outlets)
echo "REST_RESPONSE: $REST_RESPONSE"
REST_ID=$(echo $REST_RESPONSE | jq -r '.data.id')
echo "Restaurant (Outlet) ID: $REST_ID"

echo "2. Adding Master Menu Item..."
MENU_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $BRAND_OWNER_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d '{"name": "Veggie Burger", "basePrice": 150.00, "defaultPrepTimeMinutes": 15}' http://localhost:8094/api/v1/brands/$BRAND_ID/master-menu)
echo "MENU_RESPONSE: $MENU_RESPONSE"

MENU_ID=$(echo $MENU_RESPONSE | jq -r '.data.id')
echo "Menu Item ID: $MENU_ID"

echo "3. Creating Customer and Address in DB..."
CUST_PHONE="999$(printf "%07d" $RANDOM$RANDOM | cut -c1-7)"
ADDR_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
docker exec -u postgres shared_postgres psql -U postgres -d food_delivery -c "INSERT INTO customers (id, name, phone_number) VALUES ('$CUST_ID', 'Test Customer', '$CUST_PHONE');"
docker exec -u postgres shared_postgres psql -U postgres -d food_delivery -c "INSERT INTO customer_addresses (id, customer_id, address_line1, city, state, zip_code, latitude, longitude) VALUES ('$ADDR_ID', '$CUST_ID', '123 Test St', 'Bangalore', 'KA', '560001', 12.9715, 77.5945);"
echo "Customer ID: $CUST_ID, Phone: $CUST_PHONE, Address ID: $ADDR_ID"

echo "4. Creating Delivery Executive..."
DEL_PHONE="888$(printf "%07d" $RANDOM$RANDOM | cut -c1-7)"
DEL_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d "{\"name\": \"Test Exec\", \"phoneNumber\": \"$DEL_PHONE\", \"vehicleNumber\": \"KA01AB1234\"}" http://localhost:8095/api/delivery/onboard)
echo $DEL_RESPONSE
DEL_EXEC_ID_FROM_RES=$(echo $DEL_RESPONSE | jq -r '.data.id')
echo "Delivery Executive ID: $DEL_EXEC_ID_FROM_RES, Phone: $DEL_PHONE"

echo "4.5. Making Delivery Executive ONLINE and Setting Location..."
curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d "{\"driverId\": \"$DEL_EXEC_ID\", \"available\": true}" http://localhost:8095/api/delivery/status
curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d "[{\"driverId\": \"$DEL_EXEC_ID\", \"lat\": 12.9716, \"lng\": 77.5946, \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}]" http://localhost:8095/api/v1/delivery/telemetry/batch
echo ""

echo "5. Creating Order..."
ORDER_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: $CUST_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" -d "{
  \"customerId\": \"$CUST_ID\",
  \"restaurantId\": \"$REST_ID\",
  \"deliveryAddressId\": \"$ADDR_ID\",
  \"items\": [
    {
      \"menuItemId\": \"$MENU_ID\",
      \"quantity\": 2
    }
  ]
}" http://localhost:8092/api/v1/orders)
echo $ORDER_RESPONSE
ORDER_ID=$(echo $ORDER_RESPONSE | jq -r '.data.id')
PAYMENT_INTENT=$(echo $ORDER_RESPONSE | jq -r '.data.paymentIntent')
echo "Order ID: $ORDER_ID, Payment Intent: $PAYMENT_INTENT"

echo "6. Simulating Payment Webhook..."
PAYLOAD="{\"event\": \"payment.success\",\"payload\": {\"payment\": {\"entity\": {\"order_id\": \"$PAYMENT_INTENT\",\"status\": \"captured\",\"amount\": 300.00}}}}"
SIG=$(python3 -c "import hmac, hashlib; print(hmac.new(b'test_vyapar_webhook_secret', b'$PAYLOAD', hashlib.sha256).hexdigest())")
WEBHOOK_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-Id: system" -H "X-User-Roles: SYSTEM" -H "X-VyaparGateway-Signature: $SIG" -d "$PAYLOAD" http://localhost:8085/api/v1/webhooks/vyapar)
echo $WEBHOOK_RESPONSE

echo "7. Waiting for Payment to be processed by Saga..."
MAX_RETRIES=15
for (( i=1; i<=MAX_RETRIES; i++ ))
do
  ORDER_STATUS=$(docker exec -u postgres -i shared_postgres psql -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "PAID" ]; then
    echo "Order successfully transitioned to PAID."
    break
  fi
  echo "Order status is '$ORDER_STATUS' (attempt $i/$MAX_RETRIES). Waiting 2 seconds..."
  sleep 2
done

if [ "$ORDER_STATUS" != "PAID" ]; then
  echo "Order failed to transition to PAID within time limit. Exiting."
  exit 1
fi

sleep 3
echo "8. Simulating Restaurant Acceptance..."
ACCEPT_RESPONSE=$(curl -s -X POST -H "X-User-Id: $BRAND_OWNER_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" http://localhost:8094/api/v1/restaurants/$REST_ID/fulfillment/orders/$ORDER_ID/accept)
echo $ACCEPT_RESPONSE

echo "9. Simulating Driver Acceptance..."
sleep 2 
DRIVER_ACCEPT_RESPONSE=$(curl -s -X POST -H "X-User-Id: $DEL_EXEC_ID" -H "X-User-Roles: RESTAURANT,DELIVERY,CUSTOMER" http://localhost:8095/api/delivery/drivers/$DEL_EXEC_ID/orders/$ORDER_ID/accept)
echo $DRIVER_ACCEPT_RESPONSE

echo "10. Waiting for Saga to dispatch driver..."
for (( i=1; i<=MAX_RETRIES; i++ ))
do
  ORDER_STATUS=$(docker exec -u postgres -i shared_postgres psql -U postgres -d food_delivery -t -c "SELECT status FROM orders WHERE id = '$ORDER_ID';" | xargs)
  if [ "$ORDER_STATUS" == "PICKED_UP" ]; then
    echo "Order successfully transitioned to PICKED_UP."
    break
  fi
  echo "Order status is '$ORDER_STATUS' (attempt $i/$MAX_RETRIES). Waiting 2 seconds..."
  sleep 2
done

if [ "$ORDER_STATUS" != "PICKED_UP" ]; then
  echo "Order failed to transition to PICKED_UP within time limit."
fi

echo "11. Checking Order Status (Should be PICKED_UP with Driver ID)..."
docker exec -u postgres -i shared_postgres psql -U postgres -d food_delivery -c "SELECT id, status, delivery_executive_id FROM orders WHERE id = '$ORDER_ID';"

