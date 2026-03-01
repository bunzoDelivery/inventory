#!/usr/bin/env bash
# End-to-end test: Product Service → Seed Data → Search Service → Verify Sync & APIs
#
# Prerequisites:
#   1. Docker running (docker-compose-dev.yml for MySQL + Meilisearch)
#   2. Java 17, Maven
#
# Usage: ./scripts/test-search-flow.sh

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$BASE_DIR"

PRODUCT_URL="${PRODUCT_URL:-http://localhost:8081}"
SEARCH_URL="${SEARCH_URL:-http://localhost:8083}"
STORE_ID="${STORE_ID:-1}"

echo "=========================================="
echo "Quick Commerce - Search Flow Test"
echo "=========================================="
echo "Product Service: $PRODUCT_URL"
echo "Search Service:  $SEARCH_URL"
echo "Store ID:        $STORE_ID"
echo ""

# --- Step 0: Start infrastructure ---
echo "=== Step 0: Starting MySQL + Meilisearch ==="
docker-compose -f docker-compose-dev.yml up -d
echo "Waiting for MySQL to be ready..."
sleep 15

# Create inventory database (app uses 'inventory', docker-compose-dev creates quickcommerce_dev)
echo "Creating inventory database if needed..."
docker exec inventory-mysql-dev mysql -uroot -prootpassword -e "CREATE DATABASE IF NOT EXISTS inventory;" 2>/dev/null || true

# --- Step 1: Run migrations ---
echo ""
echo "=== Step 1: Running Flyway migrations ==="
mvn flyway:migrate -pl common -q -Dflyway.url="jdbc:mysql://localhost:3306/inventory" -Dflyway.user=root -Dflyway.password=rootpassword

# Ensure store exists
echo "Ensuring store exists..."
docker exec inventory-mysql-dev mysql -uroot -prootpassword inventory -e "
  INSERT IGNORE INTO stores (id, name, address, latitude, longitude, serviceable_radius_km, is_active)
  VALUES (1, 'Main Store', '123 Main St', 37.7749, -122.4194, 10, TRUE);
" 2>/dev/null || true

# --- Step 2: Start Product Service (background) ---
echo ""
echo "=== Step 2: Starting Product Service ==="
DB_HOST=localhost DB_PORT=3306 DB_NAME=inventory DB_USERNAME=root DB_PASSWORD=rootpassword \
  mvn spring-boot:run -pl product-service -Dspring-boot.run.profiles=dev -q &
PRODUCT_PID=$!
echo "Product service PID: $PRODUCT_PID"
echo "Waiting for product service to start..."
for i in {1..60}; do
  if curl -s -o /dev/null -w "%{http_code}" "$PRODUCT_URL/actuator/health" 2>/dev/null | grep -q 200; then
    echo "Product service is up!"
    break
  fi
  sleep 2
  if [ $i -eq 60 ]; then
    echo "ERROR: Product service failed to start"
    kill $PRODUCT_PID 2>/dev/null || true
    exit 1
  fi
done

# --- Step 3: Seed products and inventory ---
echo ""
echo "=== Step 3: Seeding products and inventory ==="
BASE_URL="$PRODUCT_URL" STORE_ID="$STORE_ID" ./scripts/seed-categories-and-products.sh
echo ""

# --- Step 4: Start Search Service (foreground for logs, or background) ---
echo ""
echo "=== Step 4: Starting Search Service ==="
DB_URL="r2dbc:mysql://localhost:3306/inventory" DB_USERNAME=root DB_PASSWORD=rootpassword \
  MEILI_HOST=http://localhost:7700 MEILI_MASTER_KEY=masterKey \
  CATALOG_SERVICE_URL=$PRODUCT_URL INVENTORY_SERVICE_URL=$PRODUCT_URL \
  mvn spring-boot:run -pl search-service -Dspring-boot.run.profiles=dev -q &
SEARCH_PID=$!
echo "Search service PID: $SEARCH_PID"
echo "Waiting for search service to start and complete sync..."
for i in {1..90}; do
  if curl -s -o /dev/null -w "%{http_code}" "$SEARCH_URL/actuator/health" 2>/dev/null | grep -q 200; then
    echo "Search service is up!"
    sleep 5  # Allow startup sync to complete
    break
  fi
  sleep 2
  if [ $i -eq 90 ]; then
    echo "ERROR: Search service failed to start"
    kill $SEARCH_PID 2>/dev/null || true
    kill $PRODUCT_PID 2>/dev/null || true
    exit 1
  fi
done

# --- Step 5: Verify sync ==
echo ""
echo "=== Step 5: Verifying sync ==="
echo "Index stats:"
curl -s -u admin:admin123 "$SEARCH_URL/admin/search/index/stats" | head -20
echo ""
echo ""

# --- Step 6: Test Search APIs ---
echo "=== Step 6: Testing Search APIs ==="
echo ""
echo "--- Search for 'milk' ---"
curl -s -X POST "$SEARCH_URL/search" \
  -H "Content-Type: application/json" \
  -d '{"query":"milk","storeId":1,"page":1,"pageSize":5}' | head -30
echo ""
echo ""

echo "--- Search for 'Organic' ---"
curl -s -X POST "$SEARCH_URL/search" \
  -H "Content-Type: application/json" \
  -d '{"query":"Organic","storeId":1,"page":1,"pageSize":5}' | head -30
echo ""
echo ""

# --- Step 7: Test Admin APIs ---
echo "=== Step 7: Testing Admin APIs ==="
echo ""
echo "--- GET /admin/search/settings ---"
curl -s -u admin:admin123 "$SEARCH_URL/admin/search/settings" | head -20
echo ""
echo ""

echo "--- GET /admin/search/synonyms ---"
curl -s -u admin:admin123 "$SEARCH_URL/admin/search/synonyms" | head -10
echo ""
echo ""

echo "--- POST /admin/search/synonyms (add doodh -> milk) ---"
curl -s -u admin:admin123 -X POST "$SEARCH_URL/admin/search/synonyms" \
  -H "Content-Type: application/json" \
  -d '{"term":"doodh","synonyms":["milk"]}' | head -5
echo ""
echo ""

echo "--- POST /admin/search/sync (push config to Meilisearch) ---"
curl -s -u admin:admin123 -X POST "$SEARCH_URL/admin/search/sync" | head -5
echo ""
echo ""

echo "--- Search for 'doodh' (synonym test) ---"
curl -s -X POST "$SEARCH_URL/search" \
  -H "Content-Type: application/json" \
  -d '{"query":"doodh","storeId":1,"page":1,"pageSize":5}' | head -30
echo ""
echo ""

echo "=========================================="
echo "Test complete! Services still running."
echo "Product PID: $PRODUCT_PID"
echo "Search PID:  $SEARCH_PID"
echo "To stop: kill $PRODUCT_PID $SEARCH_PID"
echo "=========================================="
