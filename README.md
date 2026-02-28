# Quick Commerce Inventory System

A reactive microservice platform for managing products, inventory, and search in the Quick Commerce platform, built with Java 17 and Spring WebFlux.

## Overview

This system provides comprehensive product catalog management, real-time inventory tracking, and intelligent product search capabilities:

### Product Service (Port 8081)
- **Product Catalog**: Complete product lifecycle management with categories, brands, and variants
- **Inventory Management**: Real-time stock tracking, reservations, and movements
- **Bulk Sync API**: Upsert products and inventory simultaneously for efficient data synchronization

### Search Service (Port 8083)
- **Intelligent Search**: Powered by Meilisearch with relevance ranking
- **Availability Integration**: Real-time stock status in search results
- **Admin Controls**: Synonyms, settings, and index management

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

4. Check health:
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8083/actuator/health
```

## API Documentation

### Product Service APIs

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

**Get All Products**
```bash
GET /api/v1/catalog/products/all
```

**Get Product by ID**
```bash
GET /api/v1/catalog/products/{id}
```

**Get Product by SKU**
```bash
GET /api/v1/catalog/products/sku/{sku}
```

**Get Product by Slug**
```bash
GET /api/v1/catalog/products/slug/{slug}
```

**Get Products by Category**
```bash
GET /api/v1/catalog/products/category/{categoryId}
```

**Get Products by Brand**
```bash
GET /api/v1/catalog/products/brand/{brand}
```

**Search Products** (Basic catalog search)
```bash
GET /api/v1/catalog/products/search?q=milk&limit=20
```

**Get Bestsellers**
```bash
GET /api/v1/catalog/products/bestsellers?limit=10
```

**Get Price Range**
```bash
GET /api/v1/catalog/products/price-range?minPrice=10&maxPrice=100
```

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

**Response:**
```json
{
  "storeId": 1,
  "totalItems": 1,
  "successCount": 1,
  "failureCount": 0,
  "results": [
    {
      "sku": "AMUL-MILK-500ML",
      "status": "SUCCESS",
      "action": "CREATED",
      "productId": 106,
      "inventoryId": 6
    }
  ],
  "processingTimeMs": 523
}
```

#### Inventory Management

**Get Inventory by SKU**
```bash
GET /api/v1/inventory/sku/{sku}?storeId=1
```

**Check Availability (Bulk)**
```bash
POST /api/v1/inventory/availability
Content-Type: application/json

{
  "storeId": 1,
  "skus": ["AMUL-MILK-500ML", "PARLE-G-BISCUIT"]
}
```

**Check Single Availability**
```bash
GET /api/v1/inventory/availability/single?storeId=1&sku=AMUL-MILK-500ML
```

**Reserve Stock**
```bash
POST /api/v1/inventory/reserve
Content-Type: application/json

{
  "sku": "AMUL-MILK-500ML",
  "quantity": 2,
  "customerId": 123,
  "orderId": "ORD-001"
}
```

**Confirm Reservation**
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

**Cancel Reservation**
```bash
POST /api/v1/inventory/reservations/{reservationId}/cancel
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

**Get Stores for Products (Bulk)** (Used by search-service)
```bash
POST /api/v1/inventory/products/stores
Content-Type: application/json

[106, 107, 108]
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

### Search Service APIs

#### Search

**Product Search**
```bash
POST /search
Content-Type: application/json

{
  "query": "milk",
  "storeId": 1,
  "page": 1,
  "pageSize": 20
}
```

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

**Wildcard Search** (Get all products)
```bash
POST /search
Content-Type: application/json

{
  "query": "*",
  "storeId": 1,
  "pageSize": 50
}
```

#### Admin APIs (Require Authentication)

**Create Synonym**
```bash
POST /admin/search/synonyms
Authorization: Basic YWRtaW46YWRtaW4xMjM=
Content-Type: application/json

{
  "word": "dahi",
  "synonyms": ["curd", "yogurt"]
}
```

**Get All Synonyms**
```bash
GET /admin/search/synonyms
Authorization: Basic YWRtaW46YWRtaW4xMjM=
```

**Get Search Settings**
```bash
GET /admin/search/settings
Authorization: Basic YWRtaW46YWRtaW4xMjM=
```

**Update Search Setting**
```bash
PUT /admin/search/settings/{key}
Authorization: Basic YWRtaW46YWRtaW4xMjM=
Content-Type: application/json

{
  "value": "10"
}
```

**Rebuild Search Index**
```bash
POST /admin/search/index/rebuild
Authorization: Basic YWRtaW46YWRtaW4xMjM=
```

**Get Index Statistics**
```bash
GET /admin/search/index/stats
Authorization: Basic YWRtaW46YWRtaW4xMjM=
```

**Trigger Config Sync**
```bash
POST /admin/search/config/sync
Authorization: Basic YWRtaW46YWRtaW4xMjM=
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

### Application Profiles

- `dev` - Development with localhost database
- `prod` - Production with environment-based configuration
- `test` - Test profile

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
- Automatic product indexing on startup
- Store-aware search with inventory filtering
- Synonym support for better matches
- Configurable relevance ranking

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
```

### Building JAR
```bash
mvn clean package -DskipTests
```

Artifacts:
- `product-service/target/product-service-1.0.0-SNAPSHOT.jar`
- `search-service/target/search-service-1.0.0-SNAPSHOT.jar`

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

### Health Checks

Both services expose:
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

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
- Check logs in `product-service/logs/` and `search-service/logs/`
- Review health endpoints for service status
- Check circuit breaker states for downstream failures
