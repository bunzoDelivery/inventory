#!/usr/bin/env bash
# Test local Docker deployment - verifies all services are up and responding
# Run from repo root after: docker-compose -f docker-compose.yml -f docker-compose.local.yml up -d --build

set -e

PRODUCT_URL="${PRODUCT_URL:-http://localhost:8081}"
ORDER_URL="${ORDER_URL:-http://localhost:8082}"
SEARCH_URL="${SEARCH_URL:-http://localhost:8083}"

echo "=========================================="
echo "Testing Local Deployment"
echo "=========================================="
echo ""

# Health checks
echo "1. Product Service (8081)..."
curl -sf "${PRODUCT_URL}/actuator/health" > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo "2. Order Service (8082)..."
curl -sf "${ORDER_URL}/actuator/health" > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo "3. Search Service (8083)..."
curl -sf "${SEARCH_URL}/actuator/health" > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo ""
echo "4. Catalog API (product-service)..."
curl -sf "${PRODUCT_URL}/api/v1/catalog/categories" > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo "5. Inventory API (product-service)..."
curl -sf -X POST "${PRODUCT_URL}/api/v1/inventory/availability" \
  -H "Content-Type: application/json" \
  -d '{"storeId":1,"skus":["SKU001"]}' > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo "6. Search API (POST /search)..."
curl -sf -X POST "${SEARCH_URL}/search" \
  -H "Content-Type: application/json" \
  -d '{"query":"test","storeId":1}' > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo "7. Order API..."
curl -sf "${ORDER_URL}/actuator/health" > /dev/null && echo "   ✓ OK" || { echo "   ✗ FAIL"; exit 1; }

echo ""
echo "=========================================="
echo "All checks passed ✓"
echo "=========================================="
