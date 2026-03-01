#!/bin/bash
# Run this on EC2 to diagnose search sync issue

echo "=== Search-service last 100 log lines ==="
sudo docker logs search-service --tail 100 2>&1

echo ""
echo "=== Filtered for sync/error ==="
sudo docker logs search-service --tail 100 2>&1 | grep -iE "sync|error|indexed|products|catalog"

echo ""
echo "=== Meilisearch index stats ==="
curl -s "http://localhost:7700/indexes/products/stats" -H "Authorization: Bearer QuickCommerce2024Prod"

echo ""
echo "=== Test catalog API from inside container ==="
sudo docker exec search-service curl -s "http://product-service:8081/api/v1/catalog/products/all" | head -c 500

echo ""
echo "=== Test inventory API from inside container ==="
sudo docker exec search-service curl -s -X POST "http://product-service:8081/api/v1/inventory/products/stores" \
  -H "Content-Type: application/json" \
  -d '[1,2,3]'
