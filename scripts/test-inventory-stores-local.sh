#!/bin/bash

# Test the /products/stores endpoint locally with real product IDs

BASE_URL="${1:-http://54.253.97.113:8081}"

echo "=== Testing /products/stores endpoint ==="
echo "Getting first 5 product IDs from catalog..."

# Get first 5 product IDs
PRODUCT_IDS=$(curl -s "$BASE_URL/api/v1/catalog/products/all" | python3 -c "
import sys, json
try:
    products = json.load(sys.stdin)
    ids = [p['id'] for p in products[:5]]
    print(json.dumps(ids))
except:
    print('[]')
")

echo "Product IDs: $PRODUCT_IDS"
echo ""
echo "Calling POST /api/v1/inventory/products/stores..."
curl -v -X POST "$BASE_URL/api/v1/inventory/products/stores" \
  -H "Content-Type: application/json" \
  -d "$PRODUCT_IDS" \
  2>&1
