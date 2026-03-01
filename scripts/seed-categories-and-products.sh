#!/usr/bin/env bash
# Seed 5 categories and multiple products via product-service API
# Uses bulk sync API (POST /api/v1/catalog/products/sync) for products + inventory
#
# Prerequisites: A store must exist (storeId=1). If not, run:
#   mysql -e "INSERT INTO stores (name, address, latitude, longitude, serviceable_radius_km, is_active) VALUES ('Main Store', '123 Main St', 37.7749, -122.4194, 10, TRUE);"
#
# Usage: BASE_URL=http://localhost:8081 STORE_ID=1 ./scripts/seed-categories-and-products.sh
# For EC2: BASE_URL=http://YOUR_EC2_IP:8081 ./scripts/seed-categories-and-products.sh

BASE_URL="${BASE_URL:-http://localhost:8081}"
STORE_ID="${STORE_ID:-1}"

echo "Seeding categories and products at $BASE_URL (storeId=$STORE_ID)"
echo ""

# Create 5 categories
echo "=== Creating 5 categories ==="
for i in 1 2 3 4 5; do
  name="Category $i"
  slug="category-$i"
  curl -s -X POST "$BASE_URL/api/v1/catalog/categories" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$name\",\"slug\":\"$slug\",\"displayOrder\":$i,\"description\":\"Category $i description\"}" | head -1
  echo ""
done

# Bulk sync products + inventory (creates products if new, updates if exist; upserts inventory for store)
echo "=== Bulk syncing products with inventory (store $STORE_ID) ==="
curl -s -X POST "$BASE_URL/api/v1/catalog/products/sync" \
  -H "Content-Type: application/json" \
  -d "{
  \"storeId\": $STORE_ID,
  \"items\": [
    {\"sku\":\"SKU-GRO-001\",\"name\":\"Organic Milk 1L\",\"categoryId\":1,\"basePrice\":4.99,\"unitOfMeasure\":\"liter\",\"currentStock\":100,\"brand\":\"FreshFarm\",\"packageSize\":\"1L\"},
    {\"sku\":\"SKU-GRO-002\",\"name\":\"Whole Wheat Bread\",\"categoryId\":1,\"basePrice\":2.49,\"unitOfMeasure\":\"piece\",\"currentStock\":80,\"brand\":\"BakeryFresh\",\"packageSize\":\"400g\"},
    {\"sku\":\"SKU-BEV-001\",\"name\":\"Orange Juice 1L\",\"categoryId\":2,\"basePrice\":3.99,\"unitOfMeasure\":\"liter\",\"currentStock\":60,\"brand\":\"JuiceCo\",\"packageSize\":\"1L\"},
    {\"sku\":\"SKU-BEV-002\",\"name\":\"Mineral Water 500ml\",\"categoryId\":2,\"basePrice\":1.29,\"unitOfMeasure\":\"piece\",\"currentStock\":200,\"brand\":\"PureWater\",\"packageSize\":\"500ml\"},
    {\"sku\":\"SKU-SNK-001\",\"name\":\"Potato Chips 150g\",\"categoryId\":3,\"basePrice\":2.99,\"unitOfMeasure\":\"piece\",\"currentStock\":120,\"brand\":\"Crunchy\",\"packageSize\":\"150g\"},
    {\"sku\":\"SKU-SNK-002\",\"name\":\"Chocolate Bar 100g\",\"categoryId\":3,\"basePrice\":1.99,\"unitOfMeasure\":\"piece\",\"currentStock\":150,\"brand\":\"SweetTreat\",\"packageSize\":\"100g\"},
    {\"sku\":\"SKU-DRY-001\",\"name\":\"Butter 250g\",\"categoryId\":4,\"basePrice\":3.49,\"unitOfMeasure\":\"piece\",\"currentStock\":90,\"brand\":\"DairyGold\",\"packageSize\":\"250g\"},
    {\"sku\":\"SKU-DRY-002\",\"name\":\"Yogurt 500g\",\"categoryId\":4,\"basePrice\":2.79,\"unitOfMeasure\":\"piece\",\"currentStock\":70,\"brand\":\"FreshDairy\",\"packageSize\":\"500g\"},
    {\"sku\":\"SKU-HLD-001\",\"name\":\"Dish Soap 500ml\",\"categoryId\":5,\"basePrice\":4.49,\"unitOfMeasure\":\"piece\",\"currentStock\":50,\"brand\":\"CleanHome\",\"packageSize\":\"500ml\"},
    {\"sku\":\"SKU-HLD-002\",\"name\":\"Paper Towels 6-pack\",\"categoryId\":5,\"basePrice\":5.99,\"unitOfMeasure\":\"piece\",\"currentStock\":40,\"brand\":\"SoftTouch\",\"packageSize\":\"6 rolls\"}
  ]
}"

echo ""
echo ""
echo "=== Done! Verify with: ==="
echo "curl $BASE_URL/api/v1/catalog/categories"
echo "curl $BASE_URL/api/v1/catalog/products"
