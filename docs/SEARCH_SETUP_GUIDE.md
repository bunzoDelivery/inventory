# Search Setup Guide

Complete guide to all settings and configuration required for search to work in the QuickCommerce inventory system.

---

## 1. Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────┐
│ Product Service │────▶│ Search Service   │────▶│ Meilisearch  │
│ (Catalog + Inv) │     │ (Sync + Search)  │     │ (Index)      │
└─────────────────┘     └──────────────────┘     └──────────────┘
         │                        │
         │                        │
         ▼                        ▼
┌─────────────────┐     ┌──────────────────┐
│ MySQL (RDS)     │     │ MySQL (RDS)      │
│ products,       │     │ search_settings,  │
│ inventory_items │     │ search_synonyms  │
└─────────────────┘     └──────────────────┘
```

---

## 2. Meilisearch Index Settings

### 2.1 Primary Key (CRITICAL)

**What it does:** Uniquely identifies each document. Required for upserts and updates.

**Why it matters:** If not set, Meilisearch tries to infer it from field names ending in `id`. With both `id` and `categoryId`, inference fails → **all document additions fail silently** (0 indexed).

**Required value:** `"id"` (matches `ProductDocument.id`)

**How to set:**
- **At index creation:** `createIndex("products", "id")` in code, or:
  ```bash
  curl -X POST http://localhost:7700/indexes \
    -H "Authorization: Bearer YOUR_MASTER_KEY" \
    -H "Content-Type: application/json" \
    -d '{"uid":"products","primaryKey":"id"}'
  ```
- **When adding documents:** Pass as 2nd param: `index.addDocuments(json, "id")` (belt-and-suspenders)

**If wrong:** Index shows 0 documents despite sync "succeeding". Check Meilisearch tasks: `GET /tasks?limit=10` for `index_primary_key_multiple_candidates_found` errors.

---

### 2.2 Filterable Attributes

**What it does:** Fields that can be used in filter expressions. **Search will not work without these.**

**Why it matters:** The search API uses this filter:
  ```
  isActive = true AND storeIds = {storeId}
  ```
  If `storeIds` or `isActive` are not filterable, the filter fails → **0 results returned**.

**Required values:**
| Attribute     | Role |
|---------------|------|
| `storeIds`    | Filter by store availability (array field; `storeIds = 1` means "contains 1") |
| `isActive`    | Exclude inactive products |
| `brand`       | Optional: filter by brand |
| `categoryId`  | Optional: filter by category |
| `isBestseller`| Optional: filter bestsellers |

**Minimum for search to work:** `["storeIds", "isActive"]` — the rest improve UX.

**How to set:** Via `search_settings` table (see Section 4) or manually:
```bash
curl -X PATCH http://localhost:7700/indexes/products/settings \
  -H "Authorization: Bearer YOUR_MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "filterableAttributes": ["storeIds", "isActive", "brand", "categoryId", "isBestseller"]
  }'
```

---

### 2.3 Searchable Attributes

**What it does:** Fields that full-text search queries match against. Order = priority (first = highest).

**Why it matters:** When user searches "milk", Meilisearch looks in these fields. If `name` isn't searchable, product names won't match.

**Recommended order:**
1. `name` — product name (highest priority)
2. `brand` — brand name
3. `keywords` — Hindi terms, synonyms, misspellings
4. `barcode` — SKU/barcode lookup

**Default:** `["name", "brand", "keywords", "barcode"]`

**How to set:** Via `search_settings` table or Meilisearch API.

---

### 2.4 Sortable Attributes

**What it does:** Fields that can be used for sorting results (price, popularity, etc.).

**Common values:** `["price", "searchPriority", "orderCount"]`

**Note:** ProductDocument has `searchPriority` and `orderCount`; ensure names match. The migration uses `"priority"` — verify it maps to an actual field or update to `searchPriority`.

---

### 2.5 Ranking Rules

**What it does:** Determines how results are ordered when relevance is similar.

**Default:** `["words", "typo", "proximity", "attribute", "sort", "exactness"]`

**Rarely needs changing** unless you have custom ranking requirements.

---

### 2.6 Synonyms

**What it does:** Maps search terms to equivalents (e.g., "doodh" → "milk").

**Stored in:** `search_synonyms` table. Synced to Meilisearch at startup by `SearchConfigurationService`.

**Example:** Hindi "doodh" should return milk products.

---

## 3. Application Configuration (application.yml / env)

### 3.1 Meilisearch Connection

| Property | Env Var | Default | Role |
|----------|---------|---------|------|
| `meilisearch.host` | `MEILI_HOST` | `http://localhost:7700` | Meilisearch server URL. Docker: `http://meilisearch:7700` |
| `meilisearch.apiKey` | `MEILI_MASTER_KEY` | `masterKey` | API key. **Must be ≥16 chars** in production |
| `meilisearch.indexName` | `MEILI_INDEX_NAME` | `products` | Index UID |
| `meilisearch.timeout` | — | 500ms | Request timeout |

**Docker Compose:** Uses `MEILI_MASTER_KEY=QuickCommerce2024Prod` (16+ chars).

---

### 3.2 Client URLs (Catalog & Inventory)

| Property | Env Var | Default | Role |
|----------|---------|---------|------|
| `clients.catalog.url` | `CATALOG_SERVICE_URL` | `http://localhost:8081` | Product catalog API. Docker: `http://product-service:8081` |
| `clients.inventory.url` | `INVENTORY_SERVICE_URL` | `http://localhost:8081` | Inventory API (same as catalog in this setup) |

**Both must be reachable** from search-service for sync and live stock checks.

---

### 3.3 Search Behavior

| Property | Default | Role |
|----------|---------|------|
| `search.candidateLimit` | 80 | Max candidates from Meilisearch before filtering |
| `search.defaultResultLimit` | 20 | Default page size when not specified |
| `search.maxResultLimit` | 100 | Max allowed page size |

---

### 3.4 Sync Configuration

| Property | Default | Role |
|----------|---------|------|
| `search.sync.enable-on-startup` | true | Run bulk sync on search-service startup |
| `search.sync.batch-size` | 500 | Products per batch when indexing |
| `search.sync.max-retries` | 3 | Retries per batch on failure |
| `search.sync.retry-delay-ms` | 1000 | Initial retry delay |
| `search.sync.max-retry-delay-ms` | 10000 | Max retry delay (exponential backoff) |

---

### 3.5 Database (search-service)

| Property | Env Var | Role |
|----------|---------|------|
| `spring.r2dbc.url` | `DB_URL` | MySQL connection. Same DB as product-service for `search_settings`, `search_synonyms` |

---

## 4. Database: search_settings Table

**Table:** `search_settings`  
**Purpose:** Store Meilisearch settings as JSON. Synced to Meilisearch at startup by `SearchConfigurationService`.

**Schema:**
| Column | Type | Role |
|--------|------|------|
| `setting_key` | VARCHAR(50) PK | Key name |
| `setting_value` | JSON | Array of strings, e.g. `["name","brand"]` |
| `description` | VARCHAR(255) | Human-readable description |

**Required rows (from migration V7):**

| setting_key | setting_value | Role |
|-------------|---------------|------|
| `ranking_rules` | `["words","typo","proximity","attribute","sort","exactness"]` | Ranking order |
| `searchable_attributes` | `["name","brand","keywords","barcode"]` | Full-text search fields |
| `filterable_attributes` | `["storeIds","isActive","brand","categoryId","isBestseller"]` | **Critical** — filter fields |
| `sortable_attributes` | `["price","priority"]` | Sort fields |

**If table is empty:** SearchConfigurationService skips settings → Meilisearch keeps defaults. New index gets `filterableAttributes: []` → **search returns 0 results**.

**Fix:** Run migration V7 or manually insert these rows.

---

## 5. ProductDocument Fields (Index Schema)

Documents in Meilisearch must include these fields for search to work:

| Field | Type | Required for | Notes |
|-------|------|--------------|-------|
| `id` | Long | Primary key, identity | Must be unique |
| `name` | String | Search, display | |
| `brand` | String | Search, filter, display | |
| `storeIds` | List&lt;Long&gt; | **Filter** | From inventory; empty = no store |
| `isActive` | Boolean | **Filter** | Must be true for search |
| `categoryId` | Long | Filter | |
| `categoryName` | String | Display | |
| `price` | BigDecimal | Display, sort | |
| `unitText` | String | Display | |
| `imageUrl` | String | Display | |
| `keywords` | List&lt;String&gt; | Search | Hindi, synonyms |

**Sync flow:** Catalog API → ProductResponse → ProductDocument (with field mapping) → Inventory API enriches `storeIds` → Meilisearch.

---

## 6. Startup Sequence

1. **Search-service starts** → connects to Meilisearch, MySQL
2. **SearchConfigurationService.onStartup()** → reads `search_settings`, `search_synonyms` → pushes to Meilisearch
3. **IndexSyncService.onStartup()** → fetches all products from catalog → enriches with storeIds from inventory → batches → `addDocuments` to Meilisearch
4. **Index must exist** with `primaryKey: "id"` before sync. If index is missing, `createIndex` is called elsewhere (e.g. admin or first sync).

**Gotcha:** If index was created manually or by a different process **without** primary key, sync will "succeed" but 0 documents indexed. Always verify index has `primaryKey: "id"`.

---

## 7. Checklist: Search Not Working?

| Check | Command / Action |
|-------|------------------|
| Index exists with primary key | `GET /indexes/products` → `primaryKey` should be `"id"` |
| Documents in index | `GET /indexes/products/stats` → `numberOfDocuments` > 0 |
| Filterable attributes | `GET /indexes/products/settings` → `filterableAttributes` includes `storeIds`, `isActive` |
| search_settings populated | `SELECT * FROM search_settings;` → 4 rows |
| Catalog has products | `GET /api/v1/catalog/products/all` |
| Inventory has storeIds | `POST /api/v1/inventory/products/stores` with `[1,2,3]` |
| Meilisearch tasks | `GET /tasks?limit=10` → no failed `documentAdditionOrUpdate` |
| Correct API usage | `POST /search` with `{"query":"milk","storeId":1,"pageSize":20}` |

---

## 8. Manual Recovery (Index Broken)

If index was created incorrectly (no primary key, wrong settings):

```bash
# 1. Delete index
curl -X DELETE http://MEILI_HOST:7700/indexes/products \
  -H "Authorization: Bearer MEILI_MASTER_KEY"

# 2. Recreate with primary key
curl -X POST http://MEILI_HOST:7700/indexes \
  -H "Authorization: Bearer MEILI_MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"uid":"products","primaryKey":"id"}'

# 3. Set filterable attributes (if search_settings empty or not synced)
curl -X PATCH http://MEILI_HOST:7700/indexes/products/settings \
  -H "Authorization: Bearer MEILI_MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "filterableAttributes": ["storeIds","isActive","brand","categoryId","isBestseller"],
    "searchableAttributes": ["name","brand","keywords","barcode"]
  }'

# 4. Trigger sync
curl -u admin:admin123 -X POST http://SEARCH_SERVICE:8083/admin/search/index/sync-data
```

---

## 9. Summary: Minimum for Search to Work

1. **Meilisearch** running, reachable, with master key ≥16 chars
2. **Index** `products` with `primaryKey: "id"`
3. **filterableAttributes** including `storeIds` and `isActive`
4. **searchableAttributes** including `name` (and ideally brand, keywords)
5. **Documents** in index (sync completed successfully)
6. **Products** in catalog with **inventory_items** (so `storeIds` is populated)
7. **Correct API:** `POST /search` with JSON body `{query, storeId, page?, pageSize?}`
