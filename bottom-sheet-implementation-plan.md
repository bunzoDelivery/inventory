


Here is a simple, clean, and developer-friendly Design Document for this feature. You can copy-paste this directly into Notion, Jira, or your project Wiki.

---

# Feature Design Document: Variant Selection Bottom Sheet (Blinkit-style)

## 1. Objective
To implement a "Variant Bottom Sheet" in the mobile app, allowing users to select different product sizes (e.g., 500ml vs 1L) directly from the product list without navigating to a Product Details Page. This reduces friction, lowers data usage, and increases the Average Order Value (AOV) via easy upsells.

## 2. Scope & Constraints
*   **In Scope:** Modifying the backend API payloads for both the **Category Listing** and **Search** endpoints to include sibling variants.
*   **Out of Scope (for MVP):** Cross-page pagination resolution. *Note: For the MVP, the bottom sheet will only show variants that are present in the current paginated API response. We are accepting this edge case to maintain high delivery speed.*
*   **Catalog Size:** Max ~10,000 SKUs (In-memory operations will be lightning fast, sub-1ms).

## 3. Data Model Changes
To link variants together, the database must have a grouping identifier.

**Table: `Products` (or `SKUs`)**
*   Add column: `group_id` (Type: String/UUID).
*   *Rule:* All SKUs that belong to the same parent product must share the exact same `group_id`. 
*   *Example:* Amul Milk 500ml (`group_id: amul_taaza_01`), Amul Milk 1L (`group_id: amul_taaza_01`).

## 4. API Contract (Backend to Frontend)
The frontend requires a flat list of products to make the catalog look full, but each product card must contain an `available_variants` array to populate the bottom sheet when the user clicks "+ ADD".

**Expected JSON Response Form:**
```json[
  {
    "id": "sku_milk_500",
    "name": "Amul Taaza Milk",
    "size": "500ml",
    "price": 15,
    "group_id": "amul_taaza_01",
    "available_variants":[
      {
        "sku_id": "sku_milk_500",
        "size": "500ml",
        "price": 15,
        "is_in_stock": true
      },
      {
        "sku_id": "sku_milk_1000",
        "size": "1 Litre",
        "price": 28,
        "is_in_stock": true
      }
    ]
  }
]
```
*(Frontend Developers: Render the outer object as the main card. Use the `available_variants` array strictly for the Bottom Sheet UI).*

## 5. System Architecture & Flow
Instead of duplicating logic, both the Category API and Search API will fetch their respective flat lists and pass them through a **single shared in-memory grouping function**.

### Flow A: Browse by Category
1. Client calls `GET /api/categories/{id}/products?page=1`
2. Backend queries Database for SKUs where `category_id = {id}`.
3. Pass results to `attachVariantsForBottomSheet()`.
4. Return modified JSON to client.

### Flow B: Search
1. Client calls `GET /api/search?q={query}`
2. Backend queries **Meilisearch** for matching SKUs.
3. Pass Meilisearch hits to `attachVariantsForBottomSheet()`.
4. Return modified JSON to client.

## 6. Core Implementation Logic (The Grouping Function)
This utility function runs in `O(N)` time complexity, ensuring zero performance degradation. 

**Code (Node.js/JavaScript example):**
```javascript
/**
 * Takes a flat array of SKUs and injects an 'available_variants' array into each item
 * based on matching group_ids.
 */
function attachVariantsForBottomSheet(fetchedSkus) {
  const groupMap = {};

  // Pass 1: Group variants by group_id
  for (const sku of fetchedSkus) {
    if (!sku.group_id) continue; // Skip if no group_id is defined

    if (!groupMap[sku.group_id]) {
      groupMap[sku.group_id] = [];
    }
    
    // Push the minimal data required for the Bottom Sheet
    groupMap[sku.group_id].push({
      sku_id: sku.id,
      size: sku.size || sku.weight,
      price: sku.price,
      is_in_stock: sku.stock > 0
    });
  }

  // Pass 2: Attach the variant arrays back to the original SKUs
  return fetchedSkus.map(sku => {
    return {
      ...sku,
      available_variants: sku.group_id ? groupMap[sku.group_id] :[]
    };
  });
}
```

## 7. Rollout Checklist
- [ ] Add `group_id` to database schema.
- [ ] Update Catalog Management (Admin Panel) to allow assigning `group_id` when creating/editing products.
- [ ] Implement `attachVariantsForBottomSheet` utility function.
- [ ] Update Category endpoint to pipe DB results through utility function.
- [ ] Update Search endpoint to pipe Meilisearch results through utility function.
- [ ] Provide updated API payload examples to Frontend/Mobile team.