#!/usr/bin/env bash
# Run on EC2 to debug product-service startup issues
# Usage: ./scripts/debug-product-service.sh

set -e

echo "=== 1. Container status ==="
sudo docker ps -a | grep -E "product|order|search|meilisearch"

echo ""
echo "=== 2. Product-service logs (last 80 lines) ==="
sudo docker logs product-service --tail 80 2>&1

echo ""
echo "=== 3. .env check (DB vars only) ==="
grep -E "^DB_|^MEILI_" /app/.env 2>/dev/null || echo ".env not found or no DB vars"

echo ""
echo "=== 4. Test RDS connectivity from EC2 ==="
if [ -f /app/.env ]; then
  source /app/.env 2>/dev/null || true
fi
nc -zv ${DB_HOST:-quickcommerce.crys0s8colmh.ap-southeast-2.rds.amazonaws.com} 3306 2>&1 || echo "nc failed - check RDS security group allows EC2 security group on port 3306"
