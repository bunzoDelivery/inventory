#!/bin/bash

# Wipe catalog and inventory data from database
# Uses DB credentials from .env (repo root)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  source "$REPO_ROOT/.env"
  set +a
fi

mysql -h "${DB_HOST}" -P "${DB_PORT:-3306}" -u "${DB_USERNAME}" -p"${DB_PASSWORD}" "${DB_NAME}" -e "
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE stock_reservations;
TRUNCATE TABLE inventory_movements;
TRUNCATE TABLE stock_alerts;
TRUNCATE TABLE inventory_items;
TRUNCATE TABLE product_store_assortment;
TRUNCATE TABLE products;
TRUNCATE TABLE categories;
SET FOREIGN_KEY_CHECKS=1;
SELECT 'Wipe complete' AS status;
" 2>/dev/null
