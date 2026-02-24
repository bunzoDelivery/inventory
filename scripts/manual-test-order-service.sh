#!/bin/bash
# Manual test script for order-service
# Prerequisites: MySQL running on localhost:3306 with 'inventory' database, root/root
# Run product-service (8081) and order-service (8082) before executing

set -e
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8081}"
ORDER_URL="${ORDER_URL:-http://localhost:8082}"

echo "=== Order Service Manual Test ==="
echo "Product Service: $PRODUCT_URL"
echo "Order Service: $ORDER_URL"
echo ""

# ─── 1. Ensure store exists (no Store API - insert via mysql) ───
echo ">>> 1. Ensuring store exists..."
mysql -h localhost -u root -proot inventory -e "
  INSERT IGNORE INTO stores (id, name, address, latitude, longitude, serviceable_radius_km, is_active)
  VALUES (1, 'Lusaka Dark Store', '123 Independence Ave, Lusaka', -15.3875, 28.3228, 5, TRUE);
" 2>/dev/null || echo "  (mysql insert skipped - ensure store id=1 exists)"

# ─── 2. Create category via product-service ───
echo ""
echo ">>> 2. Creating category..."
CAT_RESP=$(curl -s -X POST "$PRODUCT_URL/api/v1/catalog/categories" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Beverages",
    "description": "Drinks and beverages",
    "slug": "beverages-test-'$(date +%s)'",
    "displayOrder": 1,
    "isActive": true
  }')
CAT_ID=$(echo "$CAT_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -z "$CAT_ID" ]; then
  echo "  Failed to create category. Response: $CAT_RESP"
  exit 1
fi
echo "  Created category id=$CAT_ID"

# ─── 3. Create products via product-service ───
echo ""
echo ">>> 3. Creating products..."
SKU1="MILK-500ML-$(date +%s)"
SKU2="BREAD-WHITE-$(date +%s)"

PROD1=$(curl -s -X POST "$PRODUCT_URL/api/v1/catalog/products" \
  -H "Content-Type: application/json" \
  -d "{
    \"sku\": \"$SKU1\",
    \"name\": \"Fresh Milk 500ml\",
    \"description\": \"Fresh dairy milk\",
    \"categoryId\": $CAT_ID,
    \"basePrice\": 12.50,
    \"unitOfMeasure\": \"bottle\",
    \"slug\": \"milk-500ml-$(date +%s)\",
    \"isActive\": true,
    \"isAvailable\": true
  }")
PROD2=$(curl -s -X POST "$PRODUCT_URL/api/v1/catalog/products" \
  -H "Content-Type: application/json" \
  -d "{
    \"sku\": \"$SKU2\",
    \"name\": \"White Bread\",
    \"description\": \"Fresh white loaf\",
    \"categoryId\": $CAT_ID,
    \"basePrice\": 8.00,
    \"unitOfMeasure\": \"loaf\",
    \"slug\": \"bread-white-$(date +%s)\",
    \"isActive\": true,
    \"isAvailable\": true
  }")
echo "  Created products: $SKU1, $SKU2"

# ─── 4. Add stock via product-service ───
echo ""
echo ">>> 4. Adding stock..."
curl -s -X POST "$PRODUCT_URL/api/v1/inventory/stock/add" \
  -H "Content-Type: application/json" \
  -d "{\"sku\": \"$SKU1\", \"storeId\": 1, \"quantity\": 100}" > /dev/null
curl -s -X POST "$PRODUCT_URL/api/v1/inventory/stock/add" \
  -H "Content-Type: application/json" \
  -d "{\"sku\": \"$SKU2\", \"storeId\": 1, \"quantity\": 50}" > /dev/null
echo "  Stock added for both products at store 1"

# ─── 5. Order Service API Tests ───
CUSTOMER_ID="cust-$(date +%s)"
IDEM_KEY="idem-$(date +%s)"

echo ""
echo "=== ORDER SERVICE API TESTS ==="

# 5a. Preview order
echo ""
echo ">>> 5a. POST /preview - Preview order"
PREVIEW=$(curl -s -X POST "$ORDER_URL/api/v1/orders/preview" \
  -H "Content-Type: application/json" \
  -d "{
    \"storeId\": 1,
    \"items\": [
      {\"sku\": \"$SKU1\", \"qty\": 2},
      {\"sku\": \"$SKU2\", \"qty\": 1}
    ]
  }")
echo "$PREVIEW" | head -c 500
echo ""
if echo "$PREVIEW" | grep -q "totalAmount"; then
  echo "  ✓ Preview OK"
else
  echo "  ✗ Preview FAILED"
fi

# 5b. Create order
echo ""
echo ">>> 5b. POST / - Create order"
CREATE_RESP=$(curl -s -X POST "$ORDER_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "{
    \"storeId\": 1,
    \"customerId\": \"$CUSTOMER_ID\",
    \"items\": [
      {\"sku\": \"$SKU1\", \"quantity\": 2},
      {\"sku\": \"$SKU2\", \"quantity\": 1}
    ],
    \"paymentMethod\": \"COD\",
    \"delivery\": {
      \"latitude\": -15.3875,
      \"longitude\": 28.3228,
      \"address\": \"123 Test St, Lusaka\",
      \"phone\": \"0977123456\",
      \"notes\": \"Gate code 123\"
    }
  }")
ORDER_UUID=$(echo "$CREATE_RESP" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
echo "$CREATE_RESP" | head -c 600
echo ""
if [ -n "$ORDER_UUID" ]; then
  echo "  ✓ Order created: $ORDER_UUID"
else
  echo "  ✗ Create order FAILED. Response: $CREATE_RESP"
  exit 1
fi

# 5c. Get order by UUID
echo ""
echo ">>> 5c. GET /{orderUuid} - Get order"
GET_ORDER=$(curl -s "$ORDER_URL/api/v1/orders/$ORDER_UUID")
echo "$GET_ORDER" | head -c 400
echo ""
if echo "$GET_ORDER" | grep -q "orderId\|orderUuid"; then
  echo "  ✓ Get order OK"
else
  echo "  ✗ Get order FAILED"
fi

# 5d. Get customer orders
echo ""
echo ">>> 5d. GET /customer/{customerId} - Customer orders"
CUST_ORDERS=$(curl -s "$ORDER_URL/api/v1/orders/customer/$CUSTOMER_ID")
echo "$CUST_ORDERS" | head -c 300
echo ""
if echo "$CUST_ORDERS" | grep -q "orderId\|orderUuid\|\[\]"; then
  echo "  ✓ Customer orders OK"
else
  echo "  ✗ Customer orders FAILED"
fi

# 5e. Get store orders
echo ""
echo ">>> 5e. GET /store/{storeId} - Store orders"
STORE_ORDERS=$(curl -s "$ORDER_URL/api/v1/orders/store/1")
echo "$STORE_ORDERS" | head -c 300
echo ""
if echo "$STORE_ORDERS" | grep -q "orderId\|orderUuid\|\[\]"; then
  echo "  ✓ Store orders OK"
else
  echo "  ✗ Store orders FAILED"
fi

# 5f. Mock payment (for digital payment flow)
echo ""
echo ">>> 5f. POST /{orderUuid}/pay-mock - Mock payment"
PAY_RESP=$(curl -s -X POST "$ORDER_URL/api/v1/orders/$ORDER_UUID/pay-mock")
echo "$PAY_RESP" | head -c 400
echo ""
if echo "$PAY_RESP" | grep -q "PAID\|CONFIRMED"; then
  echo "  ✓ Mock payment OK"
else
  echo "  ✗ Mock payment response: $PAY_RESP"
fi

# 5g. Update status (packing)
echo ""
echo ">>> 5g. POST /{orderUuid}/status - Update to PACKING"
STATUS_RESP=$(curl -s -X POST "$ORDER_URL/api/v1/orders/$ORDER_UUID/status" \
  -H "Content-Type: application/json" \
  -H "Actor-Id: staff-1" \
  -d '{"status": "PACKING", "notes": "Started packing"}')
echo "$STATUS_RESP" | head -c 400
echo ""
if echo "$STATUS_RESP" | grep -q "PACKING"; then
  echo "  ✓ Status update OK"
else
  echo "  ✗ Status update FAILED"
fi

# 5h. Create second order for cancel test
echo ""
echo ">>> 5h. Create second order for cancel test..."
IDEM_KEY2="idem-cancel-$(date +%s)"
CREATE2=$(curl -s -X POST "$ORDER_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY2" \
  -d "{
    \"storeId\": 1,
    \"customerId\": \"$CUSTOMER_ID\",
    \"items\": [{\"sku\": \"$SKU1\", \"quantity\": 1}],
    \"paymentMethod\": \"COD\",
    \"delivery\": {
      \"latitude\": -15.3875,
      \"longitude\": 28.3228,
      \"address\": \"123 Test St\",
      \"phone\": \"0977123456\"
    }
  }")
ORDER_UUID_2=$(echo "$CREATE2" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
echo "  Order to cancel: $ORDER_UUID_2"

# 5i. Cancel order
echo ""
echo ">>> 5i. POST /{orderUuid}/cancel - Cancel order"
CANCEL_RESP=$(curl -s -X POST "$ORDER_URL/api/v1/orders/$ORDER_UUID_2/cancel" \
  -H "Content-Type: application/json" \
  -H "Customer-Id: $CUSTOMER_ID" \
  -d '{"reason": "Changed my mind"}')
echo "$CANCEL_RESP" | head -c 400
echo ""
if echo "$CANCEL_RESP" | grep -q "CANCELLED"; then
  echo "  ✓ Cancel order OK"
else
  echo "  ✗ Cancel FAILED: $CANCEL_RESP"
fi

# 5j. Idempotency - duplicate create with same key
echo ""
echo ">>> 5j. Idempotency - Duplicate create (same Idempotency-Key)"
DUP_RESP=$(curl -s -X POST "$ORDER_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "{
    \"storeId\": 1,
    \"customerId\": \"$CUSTOMER_ID\",
    \"items\": [{\"sku\": \"$SKU1\", \"quantity\": 5}],
    \"paymentMethod\": \"COD\",
    \"delivery\": {
      \"latitude\": -15.3875,
      \"longitude\": 28.3228,
      \"address\": \"123 Test St\",
      \"phone\": \"0977123456\"
    }
  }")
DUP_UUID=$(echo "$DUP_RESP" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
if [ "$DUP_UUID" = "$ORDER_UUID" ]; then
  echo "  ✓ Idempotency OK - returned same order"
else
  echo "  ✗ Idempotency FAILED - got different order: $DUP_UUID vs $ORDER_UUID"
fi

echo ""
echo "=== Manual test complete ==="
