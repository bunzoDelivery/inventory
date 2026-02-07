# Order Service MVP Review & Implementation Plan

## Executive Summary
The `order-service-mvp` design provides a solid foundation for the Order Service, particularly the "Logic Fork" for COD vs. Digital Payments and the "Future-Proof" Notification pattern. 

However, a review of the existing `product-service` (which houses both **Catalog** and **Inventory** domains) reveals **critical API gaps** that must be addressed before the Order Service can function as designed. The current `product-service` endpoints operate primarily on *single items*, while the Order Service logic requires *bulk/transactional* operations to ensure data integrity and performance.

---

## Critical Gaps & Required Changes

### 1. Catalog Service Integration (Price Check)
**Current State:**
*   `ProductController` in `product-service` exposes endpoints for single product retrieval (`GET /sku/{sku}`, `GET /{id}`) or filtering (`GET /category/{id}`).
*   **Gap:** There is **no bulk endpoint** to fetch prices for a list of SKUs in one go.
*   **Impact:** The `OrderService` would have to make N HTTP calls for an order with N items, which is inefficient and slow.

**Recommendation:**
*   **[Action Required]** Add a new endpoint to `ProductController`:
    *   `POST /api/v1/catalog/products/prices` (or `/batch`)
    *   **Input:** `List<String> skuList`
    *   **Output:** `Map<String, BigDecimal>` or `List<ProductPriceDto>`

### 2. Inventory Service Integration (Stock Reservation)
**Current State:**
*   `InventoryController` exposes `POST /api/v1/inventory/reserve` which accepts `ReserveStockRequest`.
*   `ReserveStockRequest` only supports a **single item** (`sku`, `quantity`).
*   **Gap:** `OrderService` needs to reserve stock for an *entire order* (multiple items) atomically. If we loop and reserve one by one, a failure in the 3rd item leaves the first 2 reserved (partial reservation), requiring complex rollback logic.
*   **Impact:** Risk of inconsistent inventory states and complex error handling in Order Service.

**Recommendation:**
*   **[Action Required]** Add a bulk reservation endpoint to `InventoryController`:
    *   `POST /api/v1/inventory/reserve/bulk`
    *   **Input:** `BulkReserveStockRequest` (containing `orderId`, `customerId`, list of `ItemRequest`)
    *   **Output:** `StockReservationResponse` (Success/Failure for the whole batch)
*   **[Action Required]** Update `InventoryService` logic to wrap the reservation of all items in a single database transaction.

### 3. Inventory Service Integration (Commit/Confirm)
**Current State:**
*   `OrderService` MVP logic uses `inventoryClient.commitStock(orderId, items)` for COD (immediate deduction).
*   `InventoryController` exposes `POST /api/v1/inventory/reservations/{reservationId}/confirm`.
*   **Gap:**
    *   The `confirm` endpoint relies on a `reservationId`.
    *   There is no direct "immediate deduct" endpoint for a list of items.
    *   The MVP implies "Combined Reserve & Commit" for COD.

**Recommendation:**
*   **Standardize Flow:** Update `OrderService` logic to **always** use the `Reserve -> Confirm` pattern, even for COD.
    1.  **Reserve** (Bulk) -> Gets lock.
    2.  **Save Order** to DB.
    3.  **Confirm** (Inventory) -> Deducts stock permanently.
*    This simplifies `InventoryService` (no need for a special "direct deduct" API) and keeps the "Reservation ID" concept consistent.

---

## Detailed Implementation Plan

### Phase 1: Enhance Product Service APIs
Before building `OrderService`, we need to prepare the backend.

1.  **Modify `ProductController`**:
    *   Implement `POST /api/v1/catalog/products/batch` to return details (including price) for a list of SKUs.

2.  **Modify `InventoryController`**:
    *   Create `BulkReserveStockRequest` DTO.
    *   Implement `POST /api/v1/inventory/reserve/bulk`.
    *   Ensure `InventoryService` handles this transactionally.

### Phase 2: Build Order Service
Once the dependencies are ready, implementation can proceed as per MVP design with these adjustments:

1.  **Dependencies**: `spring-boot-starter-webflux`, `spring-boot-starter-data-r2dbc`, `mysql-connector-j` (for R2DBC use `dev.miku:r2dbc-mysql` or `io.asyncer:r2dbc-mysql`).
2.  **Clients (`WebClient`)**:
    *   `CatalogClient`: Uses the new Bulk Price API.
    *   `InventoryClient`: Uses the new Bulk Reserve API and the existing Confirm/Cancel APIs.
3.  **Data Model**:
    *   `orders` table: Add `reservation_id` column (String) to store the ID returned by Inventory Service (if different from Order UUID).
4.  **Logic Update**:
    *   **COD Flow:** `Reserve (Bulk)` -> `Save Order (CONFIRMED)` -> `Confirm Reservation` -> `Notify`.
    *   **Payment Flow:** `Reserve (Bulk)` -> `Save Order (PENDING)` -> `Return Response`.
    *   **Mock Payment:** `Load Order` -> `Confirm Reservation` -> `Update Order (PAID)` -> `Notify`.

### Phase 3: Cleanup Scheduler
*   The `OrderCleanupScheduler` remains crucial.
*   **Logic:** Find `PENDING_PAYMENT` orders older than X minutes -> Call `InventoryClient.cancelReservation(reservationId)` -> Update Order to `CANCELLED`.

---

## Questions for User
1.  Do you want me to implement the **Bulk APIs** in `product-service` first? (Highly Recommended)
2.  Should we use the Order UUID as the Reservation ID to simplify tracking?
