# Search Service - Quick Start Guide

## Prerequisites

1. **Maven** - Ensure Maven is in your PATH
2. **Docker** - For running Meilisearch locally
3. **Java 17** - Required by Spring Boot 3.2.0

## Local Setup

### 1. Start Meilisearch

```bash
cd d:\bunzo\inventory
docker-compose -f docker-compose-dev.yml up -d meilisearch
```

**Verify:** Access http://localhost:7700/health

### 2. Run Database Migrations

```bash
# From project root
mvn flyway:migrate -pl common

# OR using inventory-service
cd inventory-service
mvn flyway:migrate
```

This creates:
- `product_store_assortment` table
- New search columns in `products` table

### 3. Start Search Service

```bash
cd d:\bunzo\inventory
mvn spring-boot:run -pl search-service -Dspring-boot.run.profiles=dev
```

**What happens:**
- Service starts on port `8083`
- Auto-creates Meilisearch index
- Seeds 10 sample products
- Ready for testing!

### 4. Test Search API

#### Basic Search
```bash
curl -X POST http://localhost:8083/search ^
  -H "Content-Type: application/json" ^
  -d "{\"query\":\"milk\",\"storeId\":1,\"limit\":5}"
```

#### Hindi Synonym Test
```bash
curl -X POST http://localhost:8083/search ^
  -H "Content-Type: application/json" ^
  -d "{\"query\":\"doodh\",\"storeId\":1,\"limit\":5}"
```

#### Brand Search
```bash
curl -X POST http://localhost:8083/search ^
  -H "Content-Type: application/json" ^
  -d "{\"query\":\"amul\",\"storeId\":1,\"limit\":10}"
```

## Admin Endpoints

### Get Index Stats
```bash
curl http://localhost:8083/admin/search/index/stats
```

### Recreate Index
```bash
curl -X POST http://localhost:8083/admin/search/index/create
```

### Update Settings (synonyms, etc.)
```bash
curl -X PUT http://localhost:8083/admin/search/index/settings
```

## Build & Package

```bash
# Clean build
mvn clean install

# Build only search-service
mvn clean install -pl search-service -am

# Skip tests (faster)
mvn clean install -DskipTests
```

## Troubleshooting

**Issue:** Maven not found  
**Solution:** Add Maven bin directory to PATH environment variable

**Issue:** Meilisearch connection refused  
**Solution:** Ensure Meilisearch container is running: `docker ps | findstr meilisearch`

**Issue:** No search results  
**Solution:** Check if index exists and has data: `curl http://localhost:8083/admin/search/index/stats`

## Next Steps

- **Phase 4:** Implement product indexing from catalog service
- **Phase 5:** Add comprehensive unit and integration tests
