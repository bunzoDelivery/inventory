# Bulk Product & Inventory Sync API - User Guide

## Overview

The new `/api/v1/catalog/products/sync` endpoint allows you to create or update products with inventory data in a single API call. It's designed for efficient bulk operations when refreshing inventory from external systems.

---

## API Endpoint

**URL:** `POST /api/v1/catalog/products/sync`

**Purpose:** Upsert products and inventory for a specific store

**Features:**
- Create new products if SKU doesn't exist
- Update existing products if SKU exists
- Create or update inventory for the specified store
- Process up to 500 items per request
- Partial success handling (some items can succeed while others fail)
- Automatic cache invalidation
- Internal batching for optimal performance

---

## Request Format

### Structure (Store-Centric)

```json
{
  "storeId": 1,
  "items": [
    {
      "sku": "MILK-001",
      "name": "Full Cream Milk",
      "description": "Fresh full cream milk 1 liter",
      "shortDescription": "Fresh milk",
      "categoryId": 1,
      "brand": "Amul",
      "basePrice": 65.00,
      "unitOfMeasure": "liter",
      "packageSize": "1L",
      "images": "https://cdn.example.com/milk.jpg",
      "tags": "dairy,milk,fresh",
      "isActive": true,
      "isAvailable": true,
      "slug": "full-cream-milk-1l",
      "nutritionalInfo": "{\"calories\": 150, \"protein\": 8}",
      "weightGrams": 1050,
      "barcode": "8901234567890",
      "currentStock": 50,
      "safetyStock": 10,
      "maxStock": 200,
      "unitCost": 55.00
    }
  ]
}
```

### Required Fields

**Product Fields:**
- `sku` (string, unique)
- `name` (string)
- `categoryId` (long)
- `basePrice` (decimal > 0)
- `unitOfMeasure` (string)
- `slug` (string, URL-friendly)

**Inventory Fields:**
- `currentStock` (integer >= 0)

**Request Level:**
- `storeId` (long, must exist in database)

### Optional Fields

**Product:**
- `description`, `shortDescription`, `brand`, `packageSize`
- `images`, `tags`, `nutritionalInfo`, `barcode`
- `weightGrams`
- `isActive` (default: true)
- `isAvailable` (default: true)

**Inventory:**
- `safetyStock` (default: 10)
- `maxStock` (default: 1000)
- `unitCost` (decimal)

---

## Response Format

```json
{
  "totalItems": 2,
  "successCount": 2,
  "failureCount": 0,
  "processingTimeMs": 450,
  "results": [
    {
      "sku": "MILK-001",
      "status": "SUCCESS",
      "operation": "CREATED",
      "productId": 101,
      "inventoryId": 201,
      "errorMessage": null
    },
    {
      "sku": "BREAD-002",
      "status": "SUCCESS",
      "operation": "UPDATED",
      "productId": 85,
      "inventoryId": 178,
      "errorMessage": null
    }
  ]
}
```

### Response Fields

- `totalItems`: Total number of items in request
- `successCount`: Number of items successfully processed
- `failureCount`: Number of items that failed
- `processingTimeMs`: Total processing time
- `results`: Array of per-item results
  - `sku`: Product SKU
  - `status`: SUCCESS or FAILED
  - `operation`: CREATED or UPDATED (only for successful items)
  - `productId`: Product ID (null if failed)
  - `inventoryId`: Inventory ID (null if failed)
  - `errorMessage`: Error description (null if successful)

---

## Usage Examples

### Example 1: Create New Products

```bash
curl -X POST http://localhost:8081/api/v1/catalog/products/sync \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": 1,
    "items": [
      {
        "sku": "MILK-FULL-001",
        "name": "Amul Full Cream Milk",
        "categoryId": 1,
        "basePrice": 65.00,
        "unitOfMeasure": "liter",
        "slug": "amul-full-cream-milk-1l",
        "currentStock": 100,
        "safetyStock": 20,
        "maxStock": 500
      },
      {
        "sku": "BREAD-WHEAT-001",
        "name": "Whole Wheat Bread",
        "categoryId": 2,
        "basePrice": 45.00,
        "unitOfMeasure": "piece",
        "slug": "whole-wheat-bread",
        "currentStock": 50,
        "safetyStock": 10,
        "maxStock": 200
      }
    ]
  }'
```

### Example 2: Update Existing Products

```bash
curl -X POST http://localhost:8081/api/v1/catalog/products/sync \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": 1,
    "items": [
      {
        "sku": "MILK-FULL-001",
        "name": "Amul Full Cream Milk (Updated)",
        "categoryId": 1,
        "basePrice": 68.00,
        "unitOfMeasure": "liter",
        "slug": "amul-full-cream-milk-1l",
        "currentStock": 80,
        "safetyStock": 20,
        "maxStock": 500
      }
    ]
  }'
```

### Example 3: Mixed Operations (Create + Update)

```bash
curl -X POST http://localhost:8081/api/v1/catalog/products/sync \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": 1,
    "items": [
      {
        "sku": "NEW-PRODUCT-001",
        "name": "New Product",
        "categoryId": 1,
        "basePrice": 50.00,
        "unitOfMeasure": "piece",
        "slug": "new-product",
        "currentStock": 100
      },
      {
        "sku": "EXISTING-001",
        "name": "Existing Product Updated",
        "categoryId": 2,
        "basePrice": 75.00,
        "unitOfMeasure": "kg",
        "slug": "existing-product",
        "currentStock": 50
      }
    ]
  }'
```

### Example 4: Large Batch (200 items)

```bash
# Generate JSON with 200 items programmatically
curl -X POST http://localhost:8081/api/v1/catalog/products/sync \
  -H "Content-Type: application/json" \
  -d @large_sync_request.json
```

---

## Error Handling

### Scenario 1: Invalid Store ID

**Request:**
```json
{
  "storeId": 999,
  "items": [...]
}
```

**Response:** `400 Bad Request`
```json
{
  "timestamp": "2026-02-01T10:30:00",
  "error": "Store not found: 999"
}
```

### Scenario 2: Partial Success

**Request:** 3 items, 1 has invalid category

**Response:** `200 OK`
```json
{
  "totalItems": 3,
  "successCount": 2,
  "failureCount": 1,
  "processingTimeMs": 850,
  "results": [
    {
      "sku": "MILK-001",
      "status": "SUCCESS",
      "operation": "CREATED",
      "productId": 101,
      "inventoryId": 201,
      "errorMessage": null
    },
    {
      "sku": "BREAD-002",
      "status": "FAILED",
      "operation": null,
      "productId": null,
      "inventoryId": null,
      "errorMessage": "Category not found: 999"
    },
    {
      "sku": "EGGS-003",
      "status": "SUCCESS",
      "operation": "UPDATED",
      "productId": 78,
      "inventoryId": 156,
      "errorMessage": null
    }
  ]
}
```

### Scenario 3: Validation Errors

**Request:** Too many items (> 500)

**Response:** `400 Bad Request`
```json
{
  "timestamp": "2026-02-01T10:30:00",
  "error": "Batch size must be between 1 and 500 items"
}
```

---

## Performance Characteristics

### Expected Response Times

| Items | Batches | Expected Time | Per Item Avg |
|-------|---------|---------------|--------------|
| 1     | 1       | 50-100ms      | 50-100ms     |
| 50    | 1       | 500-800ms     | 10-16ms      |
| 100   | 2       | 1-1.5s        | 10-15ms      |
| 200   | 4       | 2-3s          | 10-15ms      |
| 500   | 10      | 5-8s          | 10-16ms      |

### Database Operations per Item

1. **Product lookup**: 1 SELECT by SKU
2. **Product upsert**: 1 INSERT or UPDATE
3. **Inventory lookup**: 1 SELECT by storeId + SKU
4. **Inventory upsert**: 1 INSERT or UPDATE
5. **Total**: 3-4 queries per item (optimized with indexes)

### Internal Batching

- Processes items in batches of **50** (configurable)
- Batches run in **parallel** using reactive streams
- Memory efficient (doesn't load all 500 items at once)

---

## Testing Checklist

### 1. Basic Functionality

```bash
# Test 1: Create new product
POST /sync with 1 new item → Verify CREATED response

# Test 2: Update existing product
POST /sync with 1 existing SKU → Verify UPDATED response

# Test 3: Mixed operations
POST /sync with new + existing SKUs → Verify mixed CREATED/UPDATED
```

### 2. Store Validation

```bash
# Test: Invalid store
POST /sync with storeId=999 → Verify 400 error

# Test: Valid store
POST /sync with storeId=1 → Verify success
```

### 3. Batch Sizes

```bash
# Test various batch sizes
- 1 item
- 10 items
- 50 items (1 batch)
- 100 items (2 batches)
- 200 items (4 batches)
- 500 items (10 batches)
```

### 4. Error Scenarios

```bash
# Test: Duplicate SKUs in same request
POST /sync with duplicate SKUs → Check behavior

# Test: Invalid category
POST /sync with non-existent categoryId → Verify partial failure

# Test: Missing required fields
POST /sync with missing SKU → Verify validation error

# Test: Negative stock
POST /sync with currentStock=-10 → Verify validation error
```

### 5. Cache Invalidation

```bash
# Step 1: Get product (caches it)
GET /api/v1/catalog/products/sku/MILK-001

# Step 2: Update via sync
POST /sync with MILK-001 new price

# Step 3: Get product again (should show new price, not cached)
GET /api/v1/catalog/products/sku/MILK-001
```

### 6. Concurrent Requests

```bash
# Test: Multiple stores syncing simultaneously
POST /sync storeId=1 (50 items) & POST /sync storeId=2 (50 items)

# Test: Same store, different clients
2 concurrent POST /sync storeId=1
```

---

## Configuration

### Adjust Batch Size (if needed)

**File:** `application.yml`

```yaml
inventory:
  sync:
    batch-size: 50          # Items per batch (10-100)
    max-request-size: 500   # Max items per request (1-500)
```

**Tuning Guide:**
- **Smaller batches (20-30)**: Lower memory, slower overall
- **Default batches (50)**: Balanced performance
- **Larger batches (80-100)**: Faster but more memory

---

## Use Cases

### Use Case 1: Daily Inventory Refresh

**Scenario:** Supplier system sends updated inventory every morning

**Request:**
```json
{
  "storeId": 1,
  "items": [
    // 200 products with updated stock levels
  ]
}
```

**Expected:** 2-3 seconds, all UPDATED operations

### Use Case 2: New Store Opening

**Scenario:** Initial product catalog for new dark store

**Request:**
```json
{
  "storeId": 2,
  "items": [
    // 500 products for new store
  ]
}
```

**Expected:** 5-8 seconds, mixed CREATED (products) + CREATED (inventory for new store)

### Use Case 3: Price Update

**Scenario:** Update prices for category of products

**Request:**
```json
{
  "storeId": 1,
  "items": [
    // 50 items with new prices, same inventory
  ]
}
```

**Expected:** 500-800ms, all UPDATED operations

---

## OpenAPI Documentation

Access interactive API documentation:

**Swagger UI:** http://localhost:8081/swagger-ui.html

**OpenAPI Spec:** http://localhost:8081/v3/api-docs

The sync endpoint includes full request/response examples and schema definitions.

---

## Monitoring

### Logs to Monitor

```
# Successful sync
INFO  - Processing sync for store: 1 (Dark Store Central) with 200 items
INFO  - Processing batch of 50 items for store 1
INFO  - Bulk sync completed for store 1: 200 success, 0 failed, 2450ms

# Partial failure
INFO  - Processing sync for store: 1 (Dark Store Central) with 100 items
ERROR - Failed to sync SKU: INVALID-001 for store: 1 - Category not found: 999
INFO  - Bulk sync completed for store 1: 99 success, 1 failed, 1250ms

# Store validation failure
WARN  - Bulk sync validation error: Store not found: 999
```

### Metrics to Track

Once custom metrics are added:
- `product.sync.success` (counter)
- `product.sync.failure` (counter)
- `product.sync.duration` (timer)
- `product.sync.batch_size` (histogram)

---

## Best Practices

### 1. Batch Size Recommendations

- **Manual updates**: 1-50 items
- **Hourly sync**: 50-200 items
- **Daily full sync**: 200-500 items
- **Large catalogs**: Split into multiple 500-item requests

### 2. Error Handling

Always check the `failureCount` in the response:

```javascript
const response = await syncProducts(storeId, items);

if (response.failureCount > 0) {
  console.log('Some items failed:');
  response.results
    .filter(r => r.status === 'FAILED')
    .forEach(r => console.log(`${r.sku}: ${r.errorMessage}`));
}
```

### 3. Idempotency

The API is idempotent - safe to retry:
- Same request → Same result
- Updates are last-write-wins
- No duplicate products created (SKU is unique)

### 4. Concurrent Syncs

Safe to call concurrently:
- Different stores: Fully parallelizable
- Same store: Database handles optimistic locking

---

## Troubleshooting

### Issue: "Store not found"
**Solution:** Verify store exists in database
```sql
SELECT id, name FROM stores WHERE id = 1;
```

### Issue: Slow performance (> 20ms per item)
**Possible causes:**
1. Database connection pool exhausted
2. Missing indexes (run V8 migration)
3. Redis not running (caching disabled)

**Check:**
```bash
curl http://localhost:8081/actuator/health
# Verify database and redis are UP
```

### Issue: "Batch size exceeds maximum"
**Solution:** Split request into multiple calls
```javascript
// Split 1000 items into 2 requests of 500
const batch1 = items.slice(0, 500);
const batch2 = items.slice(500, 1000);

await syncProducts(storeId, batch1);
await syncProducts(storeId, batch2);
```

### Issue: Cache not invalidating
**Symptom:** Old data still returned after sync

**Solution:** Verify Redis is running
```bash
docker ps | grep redis
```

---

## Limitations

1. **Max 500 items per request** (configurable)
2. **Single store per request** (by design)
3. **No nested category creation** (category must exist)
4. **Inventory per store** (same SKU can have different inventory per store)

---

## Database Schema Reference

### Products Table
- Primary key: `id`
- Unique constraint: `sku`
- Foreign key: `category_id`

### Inventory Items Table
- Primary key: `id`
- Unique constraint: `(store_id, sku)`
- Foreign keys: `product_id`, `store_id`

**Important:** SKU is globally unique for products, but inventory is per store.

---

## Integration Example (Node.js)

```javascript
async function syncInventory(storeId, products) {
  const response = await fetch('http://localhost:8081/api/v1/catalog/products/sync', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      storeId,
      items: products.map(p => ({
        sku: p.sku,
        name: p.name,
        categoryId: p.categoryId,
        basePrice: p.price,
        unitOfMeasure: p.unit,
        slug: p.slug,
        currentStock: p.stock,
        safetyStock: p.minStock || 10,
        maxStock: p.maxStock || 1000
      }))
    })
  });
  
  const result = await response.json();
  
  if (result.failureCount > 0) {
    console.warn(`${result.failureCount} items failed`);
    result.results
      .filter(r => r.status === 'FAILED')
      .forEach(r => console.error(`${r.sku}: ${r.errorMessage}`));
  }
  
  return result;
}

// Usage
const products = await fetchFromSupplierAPI();
const result = await syncInventory(1, products);
console.log(`Synced ${result.successCount} products in ${result.processingTimeMs}ms`);
```

---

## Comparison with Existing APIs

| Feature | Old Way | New Sync API |
|---------|---------|--------------|
| Create product | POST /products | POST /sync |
| Update product | PUT /products/{id} | POST /sync |
| Add inventory | POST /inventory | POST /sync |
| Batch operations | Loop N times | Single call |
| Transactions | Per call | Per item (atomic) |
| Store validation | Per call | Once per request |
| Cache invalidation | Manual | Automatic |

**Advantages:**
- 1 API call instead of N calls
- Automatic inventory linking
- Partial success handling
- Better performance with batching
- Store-centric design (no redundancy)

---

## Next Steps

1. **Test the API** with Swagger UI: http://localhost:8081/swagger-ui.html
2. **Monitor logs** for batch processing
3. **Check health** after large syncs: http://localhost:8081/actuator/health
4. **Integrate** with your external inventory system
5. **Add metrics** (optional) for sync operations

---

**Status:** ✅ Ready for use  
**Version:** 1.0  
**Last Updated:** 2026-02-01
