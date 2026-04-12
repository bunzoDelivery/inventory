# Variant Selection Bottom Sheet — Integration Guide

> **Status:** Implemented and live on `feature/bottom-sheet`.  
> **Last updated:** April 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Data Model](#2-data-model)
3. [Architecture — How It Works](#3-architecture--how-it-works)
4. [Part A — Admin Panel Integration](#4-part-a--admin-panel-integration)
5. [Part B — Customer App Integration](#5-part-b--customer-app-integration)
6. [Error Reference](#6-error-reference)

---

## 1. Overview

When a customer taps **+ ADD** on a product card (e.g. "Amul Taaza Milk"), a bottom sheet slides up showing all available size/pack variants so they can pick the one they want without leaving the listing screen.

**Before (old design):** Each API response embedded the full variant list inside every product. This broke with pagination — page 2 couldn't see the variants that were only on page 1.

**Current design:** Listing/search responses return a lightweight `groupId` per product. The app makes a separate background call to fetch all variants for the visible products at once. This works correctly regardless of pagination.

```
Listing page loads                App fetches variants in background
─────────────────────────         ────────────────────────────────────
GET /category/201?page=0          POST /groups/batch
  → products with groupId    →      { "groupIds": ["amul-taaza-milk", ...] }
                                   → { "amul-taaza-milk": [...variants] }

Customer taps + ADD               Bottom sheet opens instantly (data already cached)
```

---

## 2. Data Model

A `group_id` column links variant SKUs together in the `products` table.

| Column | Type | Description |
|---|---|---|
| `group_id` | `VARCHAR(255)` | Shared slug across all size/pack variants of the same product. `NULL` for products with no variants. |

**Examples:**

| SKU | Name | group_id |
|---|---|---|
| AMUL-MILK-500ML | Amul Taaza Milk 500ml | `amul-taaza-milk` |
| DRY-AMUL-MILK-1L | Amul Taaza Milk 1L | `amul-taaza-milk` |
| AMUL-MILK-2L | Amul Taaza Milk 2 Litres | `amul-taaza-milk` |
| MAGGI-NOODLES-70G | Maggi 2-Minute Noodles 70g | `maggi-2-minute-noodles` |
| MAGGI-NOODLES-140G | Maggi 2-Minute Noodles 140g | `maggi-2-minute-noodles` |
| VEG-POTATO-1KG | Potatoes 1kg | `NULL` (no variants) |

**`group_id` format:** lowercase, hyphen-separated slug — `brand-base-product-name`.

---

## 3. Architecture — How It Works

### Auto-generation of `group_id`

When a product is created or synced **without** an explicit `groupId`, the backend auto-generates one from brand + base product name:

1. Strip trailing size/count suffix from name (`500ml`, `1 Litre`, `1kg`, `Pack of 6`, etc.)
2. Strip brand prefix from name if it's redundant (e.g. "Amul Butter" → base name is just "Butter")
3. Slugify: `{brand}-{baseName}` → lowercase, hyphens only

```
brand="Amul", name="Amul Taaza Milk 500ml"
  → strip "500ml"    → "Amul Taaza Milk"
  → strip "Amul "    → "Taaza Milk"
  → slugify          → "amul-taaza-milk"  ✓

brand="Maggi", name="Maggi 2-Minute Noodles Pack of 6"
  → strip "Pack of 6" → "Maggi 2-Minute Noodles"
  → strip "Maggi "    → "2-Minute Noodles"
  → slugify           → "maggi-2-minute-noodles"  ✓
```

> Providing an explicit `groupId` always overrides auto-generation.

### Variant ordering

Variants are always returned **sorted by price ascending** (cheapest first). This is enforced at the database level — no client-side sorting needed.

---

## 4. Part A — Admin Panel Integration

This section is for the **admin dashboard** used to manage the product catalog.

### 4.1 Creating a Single Product

**Endpoint:** `POST /api/v1/catalog/products`

**Key field:** `groupId` (optional — auto-generated if omitted)

**When to provide `groupId` explicitly:**
- When adding a new size to an existing product group — copy the `groupId` from an existing variant (use the [list groups endpoint](#43-list-all-groups-admin))
- When the auto-generated slug doesn't match correctly

**When to omit `groupId`:**
- The system will auto-generate it from brand + product name
- Works correctly for most standard naming conventions

**Sample request — adding a new variant to an existing group:**

```http
POST /api/v1/catalog/products
Content-Type: application/json

{
  "sku": "AMUL-MILK-500ML",
  "name": "Amul Taaza Milk 500ml",
  "brand": "Amul",
  "groupId": "amul-taaza-milk",
  "categoryId": 201,
  "basePrice": 1.29,
  "unitOfMeasure": "ml",
  "packageSize": "500 ml",
  "slug": "amul-taaza-milk-500ml",
  "isActive": true,
  "isAvailable": true
}
```

**Sample response:**

```json
{
  "id": 90,
  "sku": "AMUL-MILK-500ML",
  "groupId": "amul-taaza-milk",
  "name": "Amul Taaza Milk 500ml",
  "brand": "Amul",
  "categoryId": 201,
  "basePrice": 1.29,
  "packageSize": "500 ml",
  "isActive": true,
  "isAvailable": true
}
```

---

### 4.2 Bulk Sync (Recommended for imports)

Use this endpoint to upsert multiple products at once. Supply `groupId` explicitly when syncing known variant groups, or omit it to let the system auto-generate.

**Endpoint:** `POST /api/v1/catalog/products/sync`

**Request structure:**

```json
{
  "storeId": 1,
  "items": [ ...ProductSyncItem[] ]
}
```

**Full sample — syncing 3 Amul Butter variants:**

```http
POST /api/v1/catalog/products/sync
Content-Type: application/json

{
  "storeId": 1,
  "items": [
    {
      "sku": "AMUL-BUTTER-100G",
      "name": "Amul Butter 100g",
      "brand": "Amul",
      "groupId": "amul-butter",
      "categoryId": 205,
      "basePrice": 1.99,
      "unitOfMeasure": "g",
      "packageSize": "100 g",
      "slug": "amul-butter-100g",
      "isActive": true,
      "isAvailable": true,
      "currentStock": 50
    },
    {
      "sku": "AMUL-BUTTER-250G",
      "name": "Amul Butter 250g",
      "brand": "Amul",
      "groupId": "amul-butter",
      "categoryId": 205,
      "basePrice": 3.99,
      "unitOfMeasure": "g",
      "packageSize": "250 g",
      "slug": "amul-butter-250g",
      "isActive": true,
      "isAvailable": true,
      "currentStock": 30
    },
    {
      "sku": "AMUL-BUTTER-500G",
      "name": "Amul Butter 500g",
      "brand": "Amul",
      "groupId": "amul-butter",
      "categoryId": 205,
      "basePrice": 8.99,
      "unitOfMeasure": "g",
      "packageSize": "500 g",
      "slug": "amul-butter-500g",
      "isActive": true,
      "isAvailable": true,
      "currentStock": 20
    }
  ]
}
```

**Sample response:**

```json
{
  "totalItems": 3,
  "successCount": 3,
  "failureCount": 0,
  "processingTimeMs": 45,
  "results": [
    { "sku": "AMUL-BUTTER-100G", "status": "SUCCESS", "operation": "CREATED", "productId": 31 },
    { "sku": "AMUL-BUTTER-250G", "status": "SUCCESS", "operation": "CREATED", "productId": 87 },
    { "sku": "AMUL-BUTTER-500G", "status": "SUCCESS", "operation": "CREATED", "productId": 38 }
  ]
}
```

> **Upsert behaviour:** If a SKU already exists, it's updated (not duplicated). The `operation` field in each result is either `CREATED` or `UPDATED`.

---

### 4.3 List All Groups (Admin)

Use this to discover existing group IDs before creating new variants, or to audit groupings.

**Endpoint:** `GET /api/v1/catalog/products/groups`

**Sample response:**

```json
[
  { "groupId": "amul-butter",           "variantCount": 3 },
  { "groupId": "amul-taaza-milk",       "variantCount": 3 },
  { "groupId": "bisleri-water",         "variantCount": 2 },
  { "groupId": "maggi-2-minute-noodles","variantCount": 3 },
  { "groupId": "farm-fresh-eggs",       "variantCount": 2 }
]
```

**Workflow — adding a new variant size to an existing group:**

1. Call `GET /groups` to find the right `groupId` (e.g. `"amul-taaza-milk"`)
2. Create the new product via `POST /catalog/products` or the sync endpoint, passing that exact `groupId`
3. Verify by calling `GET /groups` again — the `variantCount` for that group should have incremented

---

### 4.4 `groupId` Naming Rules

| Rule | Example |
|---|---|
| Lowercase, hyphens only | `amul-taaza-milk` ✓ `Amul_Taaza_Milk` ✗ |
| Brand prefix, then base product name | `maggi-2-minute-noodles` |
| No size/quantity in the slug | `amul-butter` not `amul-butter-100g` |
| All variants of the same product share the exact same `groupId` | 100g, 250g, 500g all → `amul-butter` |
| Single-variant products can omit `groupId` entirely | Potatoes 1kg → `groupId: null` |

---

## 5. Part B — Customer App Integration

This section is for the **mobile/web frontend** building the product listing and bottom sheet UI.

### 5.1 Overall Flow

```
Step 1: Fetch listing page
  GET /api/v1/catalog/products/category/{id}  or  POST /api/v1/search

Step 2: Collect all non-null groupIds from the response

Step 3: Fire background request immediately after Step 1
  POST /api/v1/catalog/products/groups/batch
  Body: { "groupIds": [...] }

Step 4: Cache the response keyed by groupId

Step 5: When user taps +ADD on a product card
  → Look up groupId in cache
  → If found: open bottom sheet with variant list (instant)
  → If not found or null groupId: show single-item add (no bottom sheet)
```

> **Step 3 must be non-blocking** — fire it in the background, don't await it before rendering the listing. By the time the user taps +ADD the variants are already loaded.

---

### 5.2 Step 1 — Fetch Listing

Both category and search responses now include `groupId` on each product (null for ungrouped products).

**Category listing:**

```http
GET /api/v1/catalog/products/category/201?page=0&size=20
```

**Relevant fields in response:**

```json
{
  "content": [
    {
      "id": 24,
      "sku": "DRY-AMUL-MILK-1L",
      "groupId": "amul-taaza-milk",
      "name": "Amul Taaza Milk 1L",
      "brand": "Amul",
      "basePrice": 2.49,
      "packageSize": "1L",
      "isAvailable": true
    },
    {
      "id": 1,
      "sku": "VEG-POTATO-1KG",
      "groupId": null,
      "name": "Potatoes 1kg",
      "basePrice": 3.99,
      "isAvailable": true
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 86,
    "totalPages": 5
  }
}
```

> `groupId: null` means the product has no variants — show a standard single-item add button.

---

### 5.3 Step 2+3 — Background Variant Fetch

Immediately after Step 1 resolves, collect non-null groupIds and fire the batch request. Do **not** wait for this before rendering the listing.

**Pseudocode:**

```typescript
// After listing loads
const groupIds = products
  .map(p => p.groupId)
  .filter(id => id !== null)
  .filter((id, i, arr) => arr.indexOf(id) === i); // deduplicate

if (groupIds.length > 0) {
  fetchVariantGroups(groupIds); // fire-and-forget
}
```

**Endpoint:**

```http
POST /api/v1/catalog/products/groups/batch
Content-Type: application/json

{
  "groupIds": [
    "amul-taaza-milk",
    "maggi-2-minute-noodles",
    "bisleri-water"
  ]
}
```

**Sample response:**

```json
{
  "amul-taaza-milk": [
    { "productId": 90, "sku": "AMUL-MILK-500ML", "size": "500 ml", "price": 1.29, "inStock": true },
    { "productId": 24, "sku": "DRY-AMUL-MILK-1L", "size": "1L",    "price": 2.49, "inStock": true },
    { "productId": 94, "sku": "AMUL-MILK-2L",     "size": "2 L",   "price": 4.99, "inStock": true }
  ],
  "maggi-2-minute-noodles": [
    { "productId": 93, "sku": "MAGGI-NOODLES-70G",   "size": "70 g",     "price": 0.49, "inStock": true },
    { "productId": 96, "sku": "MAGGI-NOODLES-140G",  "size": "140 g",    "price": 0.89, "inStock": true },
    { "productId": 95, "sku": "MAGGI-NOODLES-PACK6", "size": "Pack of 6","price": 2.49, "inStock": true }
  ],
  "bisleri-water": [
    { "productId": 91, "sku": "BISLERI-500ML", "size": "500 ml", "price": 0.29, "inStock": true },
    { "productId": 92, "sku": "BISLERI-2L",    "size": "2 L",    "price": 0.89, "inStock": true }
  ]
}
```

**Response field reference:**

| Field | Type | Description |
|---|---|---|
| `productId` | Long | Backend product ID — use this as the `productId` when adding to cart |
| `sku` | String | SKU code |
| `size` | String | Display label for the bottom sheet chip (maps to `packageSize`) |
| `price` | Decimal | Selling price |
| `inStock` | Boolean | Whether the variant is currently available |

> Variants are pre-sorted cheapest-first by the backend. Render them in response order.

---

### 5.4 Step 4+5 — Bottom Sheet Logic

**Pseudocode (React Native / Flutter style):**

```typescript
const variantCache = new Map<string, Variant[]>();

// Store batch response
function onVariantBatchLoaded(response: Record<string, Variant[]>) {
  for (const [groupId, variants] of Object.entries(response)) {
    variantCache.set(groupId, variants);
  }
}

// When user taps +ADD on a product card
function onAddTapped(product: Product) {
  if (!product.groupId) {
    // No variants — add directly
    addToCart(product.id, 1);
    return;
  }

  const variants = variantCache.get(product.groupId);

  if (!variants || variants.length <= 1) {
    // Only one variant or cache miss — add directly
    addToCart(product.id, 1);
    return;
  }

  // Multiple variants — show bottom sheet
  openBottomSheet(variants);
}

// When user selects a variant in the bottom sheet
function onVariantSelected(variant: Variant) {
  if (!variant.inStock) return; // disable tapping out-of-stock chips
  addToCart(variant.productId, 1);
  closeBottomSheet();
}
```

---

### 5.5 Bottom Sheet UI Behaviour

| Scenario | Behaviour |
|---|---|
| `groupId` is `null` | No bottom sheet — standard +ADD button |
| `groupId` present, 1 variant in cache | No bottom sheet — add that variant directly |
| `groupId` present, 2+ variants in cache | Open bottom sheet |
| Batch fetch not yet complete (cache miss) | Add directly using the card's `productId`; bottom sheet skipped for this tap |
| Variant `inStock: false` | Show chip as greyed out / disabled in bottom sheet |
| All variants out of stock | Do not show bottom sheet; show "Out of stock" state on card |

---

### 5.6 Pagination Behaviour

This design is **pagination-safe by construction.** The batch call collects every `groupId` visible on the current page and fetches all their variants from the backend in one shot — no matter which page the sibling variants appear on.

```
Page 1 shows: Amul Taaza Milk 500ml  (groupId: amul-taaza-milk)
Page 3 shows: Amul Taaza Milk 1L     (groupId: amul-taaza-milk)

Tapping + ADD on page 1 still shows all 3 variants (500ml, 1L, 2L)
because the batch endpoint queries by groupId directly, not by page.
```

---

### 5.7 Limits & Performance

| Constraint | Value |
|---|---|
| Max `groupIds` per batch request | 50 |
| Variants per group (soft) | Unlimited (typical: 2–6) |
| Variants ordered by | Price ascending (cheapest first) |
| Typical batch response time | < 50ms |

If a listing page shows more than 50 distinct groups (unlikely with a typical page size of 20), split into two batch calls.

---

## 6. Error Reference

### Batch endpoint (`POST /groups/batch`)

| HTTP Status | Cause | Fix |
|---|---|---|
| `400 Bad Request` | `groupIds` array is empty or missing | Always send at least one group ID |
| `400 Bad Request` | More than 50 group IDs sent | Split into multiple requests |
| `200 OK` with empty `{}` | None of the group IDs exist in the DB | Verify IDs using `GET /groups` |
| `200 OK` — some groups missing from response | Those `groupId` values have no active products | Expected — just don't show bottom sheet for those cards |

### Sync endpoint (`POST /catalog/products/sync`)

| HTTP Status | Cause | Fix |
|---|---|---|
| `400 Bad Request` | Missing required fields (SKU, name, categoryId, basePrice, currentStock) | Check validation errors in response body |
| `400 Bad Request` | `storeId` not found | Verify store exists |
| `200 OK` with `failureCount > 0` | Partial failure — some items succeeded, some failed | Check per-item `errorMessage` in `results` array |
