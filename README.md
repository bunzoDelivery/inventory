# Quick Commerce Inventory System

A reactive microservice platform for managing products, inventory, search, and orders in the Quick Commerce platform, built with Java 17 and Spring WebFlux.

## Overview

This system provides comprehensive product catalog management, real-time inventory tracking, and intelligent product search capabilities:

### Product Service (Port 8081)
- **Product Catalog**: Complete product lifecycle management with categories, brands, and variants
- **Inventory Management**: Real-time stock tracking, reservations, and movements
- **Bulk Sync API**: Upsert products and inventory simultaneously for efficient data synchronization

### Search Service (Port 8083)
- **Intelligent Search**: Powered by Meilisearch with relevance ranking
- **Query Preprocessing**: Trim, lowercase, collapse spaces, and key-repeat typo fix (e.g. `milkkkkkk` → `milkk`)
- **Availability Integration**: Real-time stock status in search results
- **Admin Controls**: Synonyms, settings (auto-bootstrap on startup), and index management

### Order Service (Port 8082)
- **Order Management**: Create, preview, cancel, and track orders
- **Payment Integration**: Airtel Money mobile payments with STK push
- **Stock Reservation**: Integrates with product-service for inventory checks and reservations
- **Order Lifecycle**: PENDING_PAYMENT → CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED
- **Idempotency**: Optional Idempotency-Key header for safe retries
- **Rate Limiting**: 5 order creations per minute, 3 payment initiations per minute

## Technology Stack

- **Java 17** - Modern Java features
- **Spring Boot 3.2.0** - Application framework
- **Spring WebFlux** - Reactive non-blocking I/O
- **Spring Data R2DBC** - Reactive database access
- **MySQL 8.0** - Primary database
- **Meilisearch** - Search engine
- **Flyway** - Database migrations
- **Resilience4j** - Circuit breaker and rate limiting
- **Micrometer** - Metrics and monitoring
- **Docker** - Containerization

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Docker and Docker Compose
- MySQL 8.0

### Using Docker Compose

1. Start infrastructure services:
```bash
docker-compose up -d
```

This starts:
- MySQL database on port 3306
- Meilisearch on port 7700 (optional, for search-service)

2. Run product-service:
```bash
cd product-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Product-service will be available on `http://localhost:8081`

3. Run search-service (optional):
```bash
cd search-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Search-service will be available on `http://localhost:8083`

4. Run order-service (requires product-service):
```bash
cd order-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Order-service will be available on `http://localhost:8082`

**Note:** In `dev` profile, the mock Airtel client auto-confirms payments after 10 seconds for faster local testing.

5. Check health:
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8082/actuator/health
```

## API Documentation

> **Tip:** Use Ctrl+F (Cmd+F on Mac) to search for endpoints by path, method, or keyword (e.g. `reserve`, `payment`, `sync`).

### Quick API Reference (Searchable)

| Service | Method | Endpoint | Description |
|---------|--------|----------|-------------|
| **Product** | GET | `/api/v1/catalog/categories` | Get all active categories |
| **Product** | GET | `/api/v1/catalog/categories/tree` | Get category tree |
| **Product** | GET | `/api/v1/catalog/categories/{id}` | Get category by ID |
| **Product** | GET | `/api/v1/catalog/categories/slug/{slug}` | Get category by slug |
| **Product** | GET | `/api/v1/catalog/categories/root` | Get root categories |
| **Product** | GET | `/api/v1/catalog/categories/{parentId}/children` | Get child categories |
| **Product** | POST | `/api/v1/catalog/categories` | Create category |
| **Product** | GET | `/api/v1/catalog/products` | Get all available products |
| **Product** | GET | `/api/v1/catalog/products/all` | Get all products (incl. inactive) |
| **Product** | GET | `/api/v1/catalog/products/{id}` | Get product by ID |
| **Product** | GET | `/api/v1/catalog/products/slug/{slug}` | Get product by slug |
| **Product** | POST | `/api/v1/catalog/products/skus` | Get products by SKU list (bulk) |
| **Product** | GET | `/api/v1/catalog/products/category/{categoryId}` | Get products by category |
| **Product** | GET | `/api/v1/catalog/products/brand/{brand}` | Get products by brand |
| **Product** | GET | `/api/v1/catalog/products/search` | Search products |
| **Product** | GET | `/api/v1/catalog/products/bestsellers` | Get bestsellers |
| **Product** | GET | `/api/v1/catalog/products/price-range` | Get products by price range |
| **Product** | POST | `/api/v1/catalog/products` | Create product |
| **Product** | POST | `/api/v1/catalog/products/sync` | Bulk sync products + inventory |
| **Product** | GET | `/api/v1/inventory/sku/{sku}` | Get inventory by SKU |
| **Product** | GET | `/api/v1/inventory/barcode/{barcode}` | Get inventory by barcode |
| **Product** | POST | `/api/v1/inventory/availability` | Check availability (bulk) |
| **Product** | GET | `/api/v1/inventory/availability/single` | Check single availability |
| **Product** | POST | `/api/v1/inventory/reserve` | Reserve stock |
| **Product** | POST | `/api/v1/inventory/reservations/{id}/confirm` | Confirm reservation |
| **Product** | POST | `/api/v1/inventory/reservations/order/{orderId}/confirm` | Confirm order reservations |
| **Product** | POST | `/api/v1/inventory/reservations/{id}/cancel` | Cancel reservation |
| **Product** | POST | `/api/v1/inventory/reservations/order/{orderId}/cancel` | Cancel order reservations |
| **Product** | POST | `/api/v1/inventory/stock/add` | Add stock |
| **Product** | GET | `/api/v1/inventory/low-stock` | Get low stock items |
| **Product** | GET | `/api/v1/inventory/replenishment` | Get items needing replenishment |
| **Product** | POST | `/api/v1/inventory/nearest-store` | Find nearest store |
| **Product** | GET | `/api/v1/inventory/product/{productId}/stores` | Get stores for product |
| **Product** | POST | `/api/v1/inventory/products/stores` | Get stores for products (bulk) |
| **Product** | GET | `/api/v1/inventory/health` | Inventory health check |
| **Search** | POST | `/search` | Product search (rate limited 100/min) |
| **Search** | GET | `/admin/search/settings` | Get search settings |
| **Search** | PUT | `/admin/search/settings` | Upsert search setting |
| **Search** | POST | `/admin/search/settings/bootstrap` | Bootstrap default settings |
| **Search** | POST | `/admin/search/sync` | Sync config to Meilisearch |
| **Search** | POST | `/admin/search/synonyms` | Create/update synonym |
| **Search** | GET | `/admin/search/synonyms` | Get all synonyms |
| **Search** | DELETE | `/admin/search/synonyms/{term}` | Delete synonym |
| **Search** | GET | `/admin/search/index/stats` | Get index statistics |
| **Search** | POST | `/admin/search/index/create` | Create index |
| **Search** | PUT | `/admin/search/index/settings` | Update index settings |
| **Search** | POST | `/admin/search/index/sync-data` | Sync products to index |
| **Search** | POST | `/admin/search/index/rebuild` | Rebuild search index |
| **Search** | DELETE | `/admin/search/index` | Delete index (dev only) |
| **Order** | POST | `/api/v1/orders/preview` | Preview order |
| **Order** | POST | `/api/v1/orders` | Create order |
| **Order** | GET | `/api/v1/orders/{orderUuid}` | Get order |
| **Order** | GET | `/api/v1/orders/customer/{customerId}` | Get customer orders |
| **Order** | GET | `/api/v1/orders/store/{storeId}` | Get store orders |
| **Order** | POST | `/api/v1/orders/{orderUuid}/pay` | Initiate payment |
| **Order** | GET | `/api/v1/orders/{orderUuid}/payment-status` | Poll payment status |
| **Order** | POST | `/api/v1/orders/{orderUuid}/cancel` | Cancel order |
| **Order** | POST | `/api/v1/orders/{orderUuid}/status` | Update order status |
| **Order** | POST | `/api/v1/orders/{orderUuid}/pay-mock` | Mock payment (dev) |
| **Order** | POST | `/api/v1/webhooks/airtel` | Airtel webhook (internal) |

---

### Product Service APIs (Port 8081)

#### Catalog - Categories

**Get All Categories (Flat Structure)**
```bash
GET /api/v1/catalog/categories
```
Returns all active categories in a flat list.

**Get Category Tree (Hierarchical Structure)**
```bash
GET /api/v1/catalog/categories/tree
```
Returns all active categories organized in a hierarchical parent-child tree structure. Each parent category includes a `children` array containing its child categories.

Example response:
```json
[
  {
    "id": 1,
    "name": "Electronics",
    "slug": "electronics",
    "displayOrder": 1,
    "isActive": true,
    "children": [
      {
        "id": 10,
        "name": "Mobile Phones",
        "slug": "mobile-phones",
        "displayOrder": 1,
        "children": []
      },
      {
        "id": 11,
        "name": "Laptops",
        "slug": "laptops",
        "displayOrder": 2,
        "children": []
      }
    ]
  }
]
```

**Get Category by ID**
```bash
GET /api/v1/catalog/categories/{id}
```

**Get Category by Slug**
```bash
GET /api/v1/catalog/categories/slug/{slug}
```

**Get Root Categories**
```bash
GET /api/v1/catalog/categories/root
```

**Get Child Categories**
```bash
GET /api/v1/catalog/categories/{parentId}/children
```

**Create Category**
```bash
POST /api/v1/catalog/categories
Content-Type: application/json

{
  "name": "Dairy",
  "slug": "dairy",
  "description": "Fresh dairy products",
  "parentId": null,
  "displayOrder": 1,
  "isActive": true
}
```

#### Catalog - Products

**Get All Available Products**
```bash
GET /api/v1/catalog/products
```
Returns only active and available products.

**Get All Products** (including inactive)
```bash
GET /api/v1/catalog/products/all
```

**Get Product by ID**
```bash
GET /api/v1/catalog/products/{id}
```

**Get Product by Slug**
```bash
GET /api/v1/catalog/products/slug/{slug}
```

**Get Products by SKU List (Bulk)**
```bash
POST /api/v1/catalog/products/skus
Content-Type: application/json

{
  "skus": ["AMUL-MILK-500ML", "PARLE-G-BISCUIT"]
}
```
Returns products for up to 100 SKUs. Response: array of `ProductResponse`.

**Get Products by Category**
```bash
GET /api/v1/catalog/products/category/{categoryId}?pageNum=0&pageSize=20
```
Query params: `pageNum` (default 0), `pageSize` (default 20, max 50). Returns `PagedProductResponse`.

**Get Products by Brand**
```bash
GET /api/v1/catalog/products/brand/{brand}
```

**Search Products** (Basic catalog search)
```bash
GET /api/v1/catalog/products/search?q=milk&limit=20
```
Query params: `q` (required), `limit` (default 50).

**Get Bestsellers**
```bash
GET /api/v1/catalog/products/bestsellers?limit=20
```
Query param: `limit` (default 20).

**Get Products by Price Range**
```bash
GET /api/v1/catalog/products/price-range?minPrice=10&maxPrice=100
```
Query params: `minPrice`, `maxPrice` (required).

**Create Product**
```bash
POST /api/v1/catalog/products
Content-Type: application/json

{
  "sku": "AMUL-MILK-500ML",
  "name": "Amul Taaza Milk 500ml",
  "categoryId": 1,
  "brand": "Amul",
  "basePrice": 25.00,
  "unitOfMeasure": "500ml",
  "isActive": true,
  "isAvailable": true
}
```

#### Product + Inventory Sync (Bulk)

**Sync Products and Inventory**
```bash
POST /api/v1/catalog/products/sync
Content-Type: application/json

{
  "storeId": 1,
  "items": [
    {
      "sku": "AMUL-MILK-500ML",
      "name": "Amul Taaza Milk 500ml",
      "categoryId": 1,
      "brand": "Amul",
      "basePrice": 25.00,
      "currentStock": 100,
      "unitOfMeasure": "500ml",
      "isActive": true
    }
  ]
}
```
Required per item: `sku`, `name`, `categoryId`, `basePrice`, `unitOfMeasure`, `currentStock`. Optional: `description`, `shortDescription`, `packageSize`, `images`, `tags`, `slug`, `nutritionalInfo`, `weightGrams`, `barcode`, `safetyStock`, `maxStock`, `unitCost`. Max 500 items per request.

**Response:**
```json
{
  "totalItems": 1,
  "successCount": 1,
  "failureCount": 0,
  "processingTimeMs": 523,
  "results": [
    {
      "sku": "AMUL-MILK-500ML",
      "status": "SUCCESS",
      "operation": "CREATED",
      "productId": 106,
      "inventoryId": 6,
      "errorMessage": null
    }
  ]
}
```

#### Inventory Management

**Get Inventory by SKU**
```bash
GET /api/v1/inventory/sku/{sku}
```
Returns first matching inventory item for the SKU (includes `storeId`, `currentStock`, `availableStock`, etc.).

**Get Inventory by Barcode**
```bash
GET /api/v1/inventory/barcode/{barcode}
```
Barcode maps to SKU. Returns inventory item.

**Check Availability (Bulk)**
```bash
POST /api/v1/inventory/availability
Content-Type: application/json

{
  "storeId": 1,
  "skus": ["AMUL-MILK-500ML", "PARLE-G-BISCUIT"]
}
```
Max 100 SKUs per request.

**Check Single Availability**
```bash
GET /api/v1/inventory/availability/single?storeId=1&sku=AMUL-MILK-500ML
```

**Reserve Stock**
```bash
POST /api/v1/inventory/reserve
Content-Type: application/json

{
  "orderId": "ORD-001",
  "customerId": "cust-123",
  "storeId": 1,
  "items": [
    { "sku": "AMUL-MILK-500ML", "quantity": 2 },
    { "sku": "PARLE-G-BISCUIT", "quantity": 1 }
  ]
}
```
`storeId` is optional (for store-scoped lookup). Returns list of `StockReservationResponse`.

**Confirm Reservation** (single item)
```bash
POST /api/v1/inventory/reservations/{reservationId}/confirm
```

**Response:**
```json
{
  "reservationId": "RES_1234567890_abc123",
  "status": "CONFIRMED",
  "message": "Reservation confirmed successfully"
}
```

**Confirm Order Reservations** (bulk)
```bash
POST /api/v1/inventory/reservations/order/{orderId}/confirm
```

**Cancel Reservation**
```bash
POST /api/v1/inventory/reservations/{reservationId}/cancel
```

**Cancel Order Reservations**
```bash
POST /api/v1/inventory/reservations/order/{orderId}/cancel
```

**Add Stock**
```bash
POST /api/v1/inventory/stock/add
Content-Type: application/json

{
  "sku": "PARLE-G-BISCUIT",
  "storeId": 1,
  "quantity": 50,
  "reason": "RESTOCK"
}
```

**Get Low Stock Items**
```bash
GET /api/v1/inventory/low-stock?storeId=1
```

**Get Items Needing Replenishment**
```bash
GET /api/v1/inventory/replenishment?storeId=1
```

**Find Nearest Store**
```bash
POST /api/v1/inventory/nearest-store
Content-Type: application/json

{
  "latitude": 12.9716,
  "longitude": 77.5946,
  "skus": ["AMUL-MILK-500ML", "PARLE-G-BISCUIT"]
}
```

**Get Stores for Product** (Used by search-service)
```bash
GET /api/v1/inventory/product/{productId}/stores
```
Returns array of store IDs.

**Get Stores for Products (Bulk)** (Used by search-service)
```bash
POST /api/v1/inventory/products/stores
Content-Type: application/json

[106, 107, 108]
```
Request: array of product IDs. Response: `Map<productId, List<storeId>>`.

**Inventory Health Check**
```bash
GET /api/v1/inventory/health
```

#### Health & Metrics

**Health Check**
```bash
GET /actuator/health
```

**Prometheus Metrics**
```bash
GET /actuator/prometheus
```

**Circuit Breaker Metrics**
```bash
GET /actuator/health/circuitBreakers
```

### Search Service APIs (Port 8083)

#### Search

**Product Search**
```bash
POST /search
Content-Type: application/json
```
Rate limited: 100 requests/minute. Returns `429 Too Many Requests` when exceeded.

**Request:**
```json
{
  "query": "milk",
  "storeId": 1,
  "page": 1,
  "pageSize": 20
}
```
- `query` (required): Search string. Use `"*"` for wildcard (get all products).
- `storeId` (required): Filter results by store.
- `page` (default 1): Page number (1-indexed).
- `pageSize` (default 20, max 100): Results per page.

**Response:**
```json
{
  "query": "milk",
  "results": [
    {
      "id": 106,
      "sku": "AMUL-MILK-500ML",
      "name": "Amul Taaza Milk 500ml",
      "brand": "Amul",
      "price": 25.00,
      "currentStock": 100,
      "availableStock": 100,
      "inStock": true,
      "availabilityStatus": "AVAILABLE"
    }
  ],
  "meta": {
    "totalHits": 1,
    "page": 1,
    "pageSize": 20,
    "totalPages": 1,
    "processingTimeMs": 45
  }
}
```

#### Admin APIs (Require Authentication)

All admin endpoints require HTTP Basic auth: `admin:admin123` and `ADMIN` role.

**Get All Settings**
```bash
GET /admin/search/settings
curl -u admin:admin123 http://localhost:8083/admin/search/settings
```

**Upsert Setting**
```bash
PUT /admin/search/settings
Content-Type: application/json

{"key":"stop_words","valueJson":"[\"a\",\"an\",\"the\"]","description":"Common stop words"}
```

**Bootstrap Default Settings** (creates defaults if table is empty)
```bash
POST /admin/search/settings/bootstrap
```
Response: `{"status":"success","settingsCreated":N,"message":"..."}`

**Sync Config to Meilisearch** (push DB settings to search engine)
```bash
POST /admin/search/sync
```
Response: `{"status":"enqueued","taskUid":"...","message":"Settings pushed to Meilisearch"}`

**Create/Update Synonym**
```bash
POST /admin/search/synonyms
Content-Type: application/json

{"term":"doodh","synonyms":["milk"]}
```

**Get All Synonyms**
```bash
GET /admin/search/synonyms
```

**Delete Synonym**
```bash
DELETE /admin/search/synonyms/{term}
```

**Get Index Statistics**
```bash
GET /admin/search/index/stats
```

**Create Index**
```bash
POST /admin/search/index/create
```

**Update Index Settings** (push DB to Meilisearch)
```bash
PUT /admin/search/index/settings
```

**Sync Products** (triggers background sync)
```bash
POST /admin/search/index/sync-data
```
Response: `202 Accepted` - `{"message":"Bulk data sync triggered in background"}`

**Rebuild Search Index** (triggers full rebuild in background)
```bash
POST /admin/search/index/rebuild
```
Response: `202 Accepted` - `{"status":"accepted","message":"Index rebuild triggered in background"}`

**Delete Index** (dev profile only)
```bash
DELETE /admin/search/index
```

#### Health & Metrics

**Health Check**
```bash
GET /actuator/health
```

**Search Metrics**
```bash
GET /actuator/metrics/search.requests.total
GET /actuator/metrics/search.duration
GET /actuator/metrics/search.results
```

### Order Service APIs (Port 8082)

Base path: `/api/v1/orders`. Rate limits: 5 order creations/min per customer, 3 payment initiations/min.

#### Checkout

**Preview Order**
```bash
POST /api/v1/orders/preview
Content-Type: application/json

{
  "storeId": 1,
  "items": [
    { "sku": "AMUL-MILK-500ML", "qty": 2 },
    { "sku": "PARLE-G-BISCUIT", "qty": 1 }
  ]
}
```

**Response:**
```json
{
  "storeId": 1,
  "totalAmount": 75.00,
  "items": [
    {
      "sku": "AMUL-MILK-500ML",
      "qty": 2,
      "unitPrice": 25.00,
      "subTotal": 50.00,
      "availableQuantity": 100
    },
    {
      "sku": "PARLE-G-BISCUIT",
      "qty": 1,
      "unitPrice": 25.00,
      "subTotal": 25.00,
      "availableQuantity": 0
    }
  ],
  "warnings": ["Insufficient stock for PARLE-G-BISCUIT"]
}
```

**Create Order**
```bash
POST /api/v1/orders
Content-Type: application/json
Idempotency-Key: optional-unique-key-for-retries

{
  "storeId": 1,
  "customerId": "cust-123",
  "items": [
    { "sku": "AMUL-MILK-500ML", "quantity": 2 },
    { "sku": "PARLE-G-BISCUIT", "quantity": 1 }
  ],
  "paymentMethod": "AIRTEL_MONEY",
  "delivery": {
    "latitude": -15.3875,
    "longitude": 28.3228,
    "address": "123 Main St, Lusaka",
    "phone": "0977123456",
    "notes": "Gate code 1234"
  }
}
```
- `paymentMethod`: `COD` | `AIRTEL_MONEY` | `MTN_MONEY`
- `items`: max 50 distinct SKUs, max 100 units per item
- `delivery.phone`: Indian (e.g. 9876543210) or Zambian (e.g. 0977123456, +260977123456)

**Response:** `201 Created`
- **COD orders**: Status `CONFIRMED` (immediately ready for fulfillment)
- **Digital payment orders** (AIRTEL_MONEY, MTN_MONEY): Status `PENDING_PAYMENT` (requires payment via `/pay` endpoint)

#### Query

**Get Order by UUID**
```bash
GET /api/v1/orders/{orderUuid}
```

**Get Customer Orders**
```bash
GET /api/v1/orders/customer/{customerId}?page=0&size=20
```
Query params: `page` (default 0), `size` (default 20, max 50).

**Get Store Orders**
```bash
GET /api/v1/orders/store/{storeId}?status=PENDING&page=0&size=20
```
Query params: `status` (optional), `page` (default 0), `size` (default 20, max 50).

#### Customer Actions

**Initiate Payment** (for AIRTEL_MONEY/MTN_MONEY orders)

**Required header:** `X-Customer-Id` (required)
```bash
POST /api/v1/orders/{orderUuid}/pay
Content-Type: application/json
X-Customer-Id: cust-123

{
  "paymentPhone": "0971234567"
}
```
`paymentPhone`: Indian or Zambian mobile number format.

**Response:** `200 OK`
```json
{
  "orderUuid": "550e8400-e29b-41d4-a716-446655440000",
  "orderStatus": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "paymentPhone": "097****567",
  "pushStatus": "PUSH_SENT",
  "message": "Airtel Money prompt sent to 097****567. Please enter your PIN."
}
```

**Poll Payment Status** (poll every 3 seconds after initiating payment)

**Required header:** `X-Customer-Id` (required)
```bash
GET /api/v1/orders/{orderUuid}/payment-status
X-Customer-Id: cust-123
```

**Response states:**
- `AWAITING_PUSH` - Before `/pay` is called
- `PUSH_SENT` - STK push sent, waiting for customer PIN
- `CONFIRMED` - Payment successful, order confirmed
- `FAILED` - Payment failed or cancelled

**Cancel Order**

**Required header:** `Customer-Id` (required)
```bash
POST /api/v1/orders/{orderUuid}/cancel
Content-Type: application/json
Customer-Id: cust-123

{
  "reason": "Changed my mind"
}
```
`reason` is required (max 255 chars).

#### Dark Store Operations

**Update Order Status**
```bash
POST /api/v1/orders/{orderUuid}/status
Content-Type: application/json
Actor-Id: SYSTEM

{
  "status": "PACKING",
  "notes": "Order picked"
}
```
Header `Actor-Id` (optional, default `SYSTEM`). Valid statuses: `PACKING`, `OUT_FOR_DELIVERY`, `DELIVERED`.

#### Dev / QA Only

**Mock Payment**
```bash
POST /api/v1/orders/{orderUuid}/pay-mock
```
Auto-confirms payment (mock-airtel profile).

#### Webhooks (Internal)

**Airtel Webhook** - Airtel POSTs here after customer enters PIN. Always returns `200 OK`.
```bash
POST /api/v1/webhooks/airtel
```

#### Health & Metrics

**Health Check**
```bash
GET /actuator/health
```

## Configuration

### Product Service Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 3306 |
| `DB_NAME` | Database name | quickcommerce |
| `DB_USERNAME` | Database username | root |
| `DB_PASSWORD` | Database password | root |
| `SERVER_PORT` | Application port | 8081 |

### Search Service Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 3306 |
| `DB_NAME` | Database name | quickcommerce |
| `DB_USERNAME` | Database username | root |
| `DB_PASSWORD` | Database password | root |
| `MEILISEARCH_URL` | Meilisearch URL | http://localhost:7700 |
| `MEILISEARCH_API_KEY` | Meilisearch master key | masterKey |
| `CATALOG_SERVICE_URL` | Product service URL | http://localhost:8081 |
| `INVENTORY_SERVICE_URL` | Product service URL | http://localhost:8081 |
| `SERVER_PORT` | Application port | 8083 |

### Order Service Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 3306 |
| `DB_NAME` | Database name | inventory |
| `DB_USERNAME` | Database username | root |
| `DB_PASSWORD` | Database password | root |
| `PRODUCT_SERVICE_URL` | Product service URL | http://localhost:8081 |
| `INVENTORY_SERVICE_URL` | Product service URL | http://localhost:8081 |
| `SERVER_PORT` | Application port | 8082 |
| `ORDER_DELIVERY_FEE` | Delivery fee (ZMW) | 15.00 |
| `ORDER_PAYMENT_TIMEOUT_MINUTES` | Payment timeout | 15 |
| `ORDER_CLEANUP_INTERVAL_MS` | Cleanup interval for expired orders | 60000 |
| `SPRING_PROFILES_ACTIVE` | Spring profiles | mock-airtel |
| `AIRTEL_CLIENT_ID` | Airtel API client ID (for `airtel` profile) | - |
| `AIRTEL_CLIENT_SECRET` | Airtel API client secret (for `airtel` profile) | - |
| `AIRTEL_CALLBACK_URL` | Airtel webhook URL (HTTPS, publicly accessible) | - |

### Application Profiles

**Product Service & Search Service:**
- `dev` - Development with localhost database
- `prod` - Production with environment-based configuration
- `test` - Test profile

**Order Service:**
- `dev` - Development with localhost database (includes mock-airtel by default)
- `mock-airtel` - Mock Airtel Money client for local/staging (no real API calls, auto-confirms after 10s)
- `airtel` - Real Airtel Money API integration (requires `AIRTEL_CLIENT_ID`, `AIRTEL_CLIENT_SECRET`, `AIRTEL_CALLBACK_URL`)

**Default:** `SPRING_PROFILES_ACTIVE=mock-airtel`

**Production with mock payment:**
```bash
SPRING_PROFILES_ACTIVE=mock-airtel \
DB_HOST=prod-db-host \
DB_USERNAME=admin \
DB_PASSWORD=secure-password \
java -jar order-service.jar
```

**Production with real Airtel API:**
```bash
SPRING_PROFILES_ACTIVE=airtel \
DB_HOST=prod-db-host \
AIRTEL_CLIENT_ID=your-client-id \
AIRTEL_CLIENT_SECRET=your-client-secret \
AIRTEL_CALLBACK_URL=https://yourdomain.com/api/v1/webhooks/airtel \
java -jar order-service.jar
```

## Architecture

### Reactive Design
- Non-blocking I/O with Spring WebFlux and R2DBC
- Reactive streams for efficient resource usage
- Backpressure handling for high-load scenarios

### Database
- MySQL 8.0 with R2DBC driver for reactive access
- Flyway migrations for schema versioning (in `common/src/main/resources/db/migration/`)
- Optimistic locking for concurrent updates

### Resilience
- Circuit breakers for external service calls (Resilience4j)
- Rate limiting on search endpoints (100 req/min)
- Retry logic with exponential backoff
- Graceful degradation when services unavailable

### Search Integration
- **Sequential startup**: Index creation → settings bootstrap → config sync → product sync
- **Query preprocessing**: Trim, lowercase, collapse multiple spaces, and collapse 3+ repeated letters (key-repeat typo fix). Digits and special chars (7Up, Coca-Cola, 500ml) are preserved.
- Auto-bootstraps default settings (ranking rules, searchable/filterable/sortable attributes) if DB is empty
- Store-aware search with inventory filtering
- Synonym support for better matches
- Configurable relevance ranking via admin APIs

### Monitoring
- Prometheus metrics for requests, latency, errors
- Health indicators for database, search engine, sync status
- Circuit breaker state monitoring
- Custom metrics for business KPIs

## Features

### Product Catalog
- ✅ Hierarchical categories with parent-child relationships
- ✅ Product variants and SKU management
- ✅ Brand management
- ✅ Slug-based URLs for SEO
- ✅ Bestseller tracking
- ✅ Image and metadata support

### Inventory Management
- ✅ Real-time stock tracking across multiple stores
- ✅ Stock reservations (15-minute TTL)
- ✅ Reserve-on-checkout mechanism
- ✅ Safety stock and replenishment alerts
- ✅ Stock movement audit trail
- ✅ Optimistic locking for concurrent updates
- ✅ Atomic operations for overselling prevention

### Bulk Sync
- ✅ Upsert products and inventory in single API call
- ✅ Batch processing (50 items per batch)
- ✅ Partial success handling
- ✅ Automatic slug generation
- ✅ Transaction management
- ✅ Detailed result reporting

### Search
- ✅ Full-text search with relevance ranking
- ✅ Query preprocessing (trim, lowercase, collapse spaces, key-repeat typo fix)
- ✅ Store-specific inventory filtering
- ✅ Real-time availability integration
- ✅ Fallback to bestsellers when no results
- ✅ Pagination support
- ✅ Synonym support for better matches
- ✅ Admin controls for tuning search behavior

## Database Schema

### Core Tables (Managed by Flyway)

**Products**
- `products` - Product catalog data
- `categories` - Hierarchical product categories

**Inventory**
- `inventory_items` - Stock levels per store
- `stock_movements` - Audit trail for stock changes
- `stock_reservations` - Temporary holds during checkout

**Stores**
- `stores` - Store information with geo-location

**Search** (search-service)
- `search_synonyms` - Search synonym mappings
- `search_settings` - Search configuration

**Orders** (order-service)
- `customer_orders` - Order header with status, payment method, delivery details
- `order_items` - Line items per order
- `order_events` - Order lifecycle event log
- `payment_attempts` - Airtel Money payment audit trail

## Development

### Running Locally

**Development Profile (localhost database):**
```bash
# Product Service
cd product-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Search Service
cd search-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Order Service (requires product-service)
cd order-service
SPRING_PROFILES_ACTIVE=dev,mock-airtel mvn spring-boot:run
```

**With Custom Database:**
```bash
DB_HOST=mydb.example.com \
DB_USERNAME=myuser \
DB_PASSWORD=mypass \
SPRING_PROFILES_ACTIVE=prod \
mvn spring-boot:run
```

### Running Tests
```bash
# All tests
mvn test

# Specific service
cd product-service
mvn test

cd order-service
mvn test
```

### Building JAR
```bash
mvn clean package -DskipTests
```

Artifacts:
- `product-service/target/product-service-1.0.0-SNAPSHOT.jar`
- `search-service/target/search-service-1.0.0-SNAPSHOT.jar`
- `order-service/target/order-service-1.0.0-SNAPSHOT.jar`

### Building Docker Image
```bash
# Update docker-compose.yml with your service
docker build -t quickcommerce/product-service:latest -f product-service/Dockerfile .
```

## Deployment (AWS)

### Recommended Setup

1. **RDS MySQL Instance**
   - MySQL 8.0
   - Multi-AZ for high availability
   - Automated backups enabled

2. **EC2 Instance for Product Service**
   - Java 17 runtime
   - Environment variables for DB connection
   - Port 8081 exposed

3. **EC2 Instance for Meilisearch** (optional, for search-service)
   - Docker with Meilisearch container
   - Port 7700 exposed
   - Persistent volume for index data

4. **Application Load Balancer**
   - Health check: `/actuator/health`
   - Target groups for product-service (and search-service if used)

### Environment Configuration

Set these environment variables on your EC2 instance:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=your-rds-endpoint.region.rds.amazonaws.com
export DB_PORT=3306
export DB_NAME=quickcommerce
export DB_USERNAME=admin
export DB_PASSWORD=your-secure-password
export SERVER_PORT=8081

# For search-service (if deploying)
export MEILISEARCH_URL=http://meilisearch-host:7700
export MEILISEARCH_API_KEY=your-master-key
export CATALOG_SERVICE_URL=http://product-service-host:8081
export INVENTORY_SERVICE_URL=http://product-service-host:8081
```

### Running on EC2

```bash
# Upload JAR
scp product-service/target/product-service-1.0.0-SNAPSHOT.jar ec2-user@your-instance:/home/ec2-user/

# SSH to instance
ssh ec2-user@your-instance

# Run as background service
nohup java -jar product-service-1.0.0-SNAPSHOT.jar > app.log 2>&1 &
```

Or use systemd service:

```bash
# Create /etc/systemd/system/product-service.service
[Unit]
Description=Quick Commerce Product Service
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/product-service-1.0.0-SNAPSHOT.jar
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_HOST=your-rds-endpoint"
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Start service:
```bash
sudo systemctl start product-service
sudo systemctl enable product-service
sudo systemctl status product-service
```

## Performance Characteristics

### Product Service
- **Throughput**: 1000+ req/sec (reactive non-blocking)
- **Latency**: p50 < 50ms, p99 < 200ms
- **Concurrency**: Handles 10,000+ concurrent reservations without overselling
- **Bulk Sync**: 500 products in ~5 seconds

### Search Service
- **Search Latency**: p50 < 100ms, p99 < 500ms
- **Index Size**: 10,000+ products
- **Refresh**: Real-time with automatic sync

### Order Service
- **Order Creation**: Rate limited (5 req/min per customer)
- **Circuit Breaker**: Protects against product-service failures
- **Idempotency**: Safe retries with Idempotency-Key header

## Monitoring

### Key Metrics

**Product Service:**
- `http.server.requests` - API request counts and latency
- `r2dbc.pool.acquired` - Database connection pool usage
- `product.sync.duration` - Bulk sync performance
- `inventory.reservations.active` - Active reservation count

**Search Service:**
- `search.requests.total` - Total search requests
- `search.duration` - Search latency
- `search.results` - Results per query
- `search.errors` - Failed searches
- `circuit.breaker.state` - Circuit breaker status

**Order Service:**
- `http.server.requests` - API request counts and latency
- `circuit.breaker.state` - Product-service circuit breaker status

### Health Checks

All services expose:
- `/actuator/health` - Overall health
- `/actuator/health/db` - Database connectivity
- `/actuator/health/circuitBreakers` - Circuit breaker status
- Custom health indicators for sync status, etc.

## Security (MVP - Public APIs)

Current MVP has all endpoints public. For production:
- Implement JWT authentication
- Add role-based access control (RBAC)
- Enable CORS with specific origins
- Add rate limiting per user/API key
- Use HTTPS/TLS for all communication

## Troubleshooting

### Product Service Not Starting
```bash
# Check if port 8081 is available
netstat -ano | findstr :8081  # Windows
lsof -i :8081                 # Linux/Mac

# Check database connectivity
mysql -h DB_HOST -u DB_USERNAME -p

# Check logs
tail -f product-service/logs/product-service-dev.log
```

### Search Returns No Results
```bash
# Check if Meilisearch is running
curl http://localhost:7700/health

# Check index stats
curl -H "Authorization: Bearer masterKey" http://localhost:7700/indexes/products/stats

# Trigger manual rebuild
curl -X POST http://localhost:8083/admin/search/index/rebuild \
  -u admin:admin123
```

### Bulk Sync Failures
- Verify all required fields in request payload
- Check database constraints (unique SKUs, category IDs exist)
- Review sync response for specific error messages
- Check product-service logs for detailed errors

### Order Service Failures
```bash
# Ensure product-service is running (order-service depends on it)
curl http://localhost:8081/actuator/health

# Check order-service health
curl http://localhost:8082/actuator/health

# Check circuit breaker state if product-service is down
curl http://localhost:8082/actuator/health/circuitBreakers
```

- **429 Too Many Requests**: Order creation rate limited (5/min per customer)
- **InsufficientStockException**: Items out of stock; use preview API first
- **ServiceUnavailableException**: Product-service unreachable; check circuit breaker

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
- Check logs in `product-service/logs/`, `search-service/logs/`, and `order-service/logs/`
- Review health endpoints for service status
- Check circuit breaker states for downstream failures
