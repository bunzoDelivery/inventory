# Search Service - Quick Start Guide

## Prerequisites

1. **Maven** - Ensure Maven is in your PATH
2. **Docker** - For running Meilisearch locally
3. **Java 17** - Required by Spring Boot 3.2.0

## Local Setup

### 1. Start Meilisearch

```bash
docker-compose -f docker-compose-dev.yml up -d meilisearch
```

**Verify:** Access http://localhost:7700/health

### 2. Run Database Migrations

```bash
# From project root
mvn flyway:migrate -pl common
```

This creates:
- `search_settings` and `search_synonyms` tables
- Other catalog/inventory tables as needed

### 3. Start Search Service

```bash
mvn spring-boot:run -pl search-service -Dspring-boot.run.profiles=dev
```

**Startup sequence (automatic):**
1. Creates Meilisearch index if missing (with `primaryKey="id"`)
2. Auto-bootstraps default settings if `search_settings` is empty
3. Syncs settings and synonyms from DB to Meilisearch
4. Syncs products from catalog service (if `sync.enable-on-startup=true`)

### 4. Test Search API

Search endpoint is public (no auth required).

#### Basic Search
```bash
curl -X POST http://localhost:8083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"milk","storeId":1,"page":1,"pageSize":5}'
```

#### Hindi Synonym Test
```bash
curl -X POST http://localhost:8083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"doodh","storeId":1,"page":1,"pageSize":5}'
```

#### Brand Search
```bash
curl -X POST http://localhost:8083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"amul","storeId":1,"page":1,"pageSize":10}'
```

## Query Preprocessing

Search queries are preprocessed before being sent to Meilisearch:

| Step | Action | Example |
|------|--------|---------|
| Trim | Remove leading/trailing whitespace | `"  milk  "` → `"milk"` |
| Collapse spaces | Multiple spaces → single space | `"milk  milk"` → `"milk milk"` |
| Lowercase | Case-insensitive matching | `"MILK"` → `"milk"` |
| Collapse repeated letters | 3+ identical letters → 2 (key-repeat typo fix) | `"milkkkkkk"` → `"milkk"` |

**Preserved:** Digits, hyphens, and special characters are left unchanged (e.g. `7Up`, `Coca-Cola`, `500ml`). Only letter repetitions are collapsed.

## Admin Endpoints

All admin endpoints require HTTP Basic auth: `admin:admin123`

### Index Management

| Action | Method | Endpoint |
|--------|--------|----------|
| Get index stats | GET | `/admin/search/index/stats` |
| Create index | POST | `/admin/search/index/create` |
| Update settings (push DB → Meilisearch) | PUT | `/admin/search/index/settings` |
| Sync products | POST | `/admin/search/index/sync-data` |
| Rebuild index | POST | `/admin/search/index/rebuild` |

### Settings Management

| Action | Method | Endpoint |
|--------|--------|----------|
| Get all settings | GET | `/admin/search/settings` |
| Upsert setting | PUT | `/admin/search/settings` |
| Bootstrap defaults (if empty) | POST | `/admin/search/settings/bootstrap` |
| Sync config to Meilisearch | POST | `/admin/search/sync` |

### Synonyms

| Action | Method | Endpoint |
|--------|--------|----------|
| Get all synonyms | GET | `/admin/search/synonyms` |
| Create/update synonym | POST | `/admin/search/synonyms` |
| Delete synonym | DELETE | `/admin/search/synonyms/{term}` |

### Example: Get Index Stats
```bash
curl -u admin:admin123 http://localhost:8083/admin/search/index/stats
```

### Example: Bootstrap Default Settings
```bash
curl -u admin:admin123 -X POST http://localhost:8083/admin/search/settings/bootstrap
```

### Example: Upsert Setting
```bash
curl -u admin:admin123 -X PUT http://localhost:8083/admin/search/settings \
  -H "Content-Type: application/json" \
  -d '{"key":"stop_words","valueJson":"[\"a\",\"an\",\"the\"]","description":"Common stop words"}'
```

### Example: Add Synonym
```bash
curl -u admin:admin123 -X POST http://localhost:8083/admin/search/synonyms \
  -H "Content-Type: application/json" \
  -d '{"term":"doodh","synonyms":["milk"]}'
```

### Example: Sync Products
```bash
curl -u admin:admin123 -X POST http://localhost:8083/admin/search/index/sync-data
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
**Solution:** Ensure Meilisearch container is running: `docker ps | grep meilisearch`

**Issue:** No search results  
**Solution:** Check if index exists and has data: `curl -u admin:admin123 http://localhost:8083/admin/search/index/stats`

**Issue:** 401 Unauthorized on admin endpoints  
**Solution:** Use HTTP Basic auth: `curl -u admin:admin123 <url>`

**Issue:** Empty index after startup  
**Solution:** Ensure product-service and catalog are reachable. Trigger manual sync: `curl -u admin:admin123 -X POST http://localhost:8083/admin/search/index/sync-data`
