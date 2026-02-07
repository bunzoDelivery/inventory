# Implementation Plan - Phase 1: Product Service API Updates

**Goal:** Enable "Bulk Operations" in Product Service (Inventory & Catalog) to support the Order Service's transactional requirements.

## User Review Required
> [!IMPORTANT]
> **Breaking Change**: The `POST /api/v1/inventory/reserve` endpoint will be modified. The request body structure will change from single-item to multi-item.
> **Breaking Change**: The `GET /api/v1/catalog/products/sku/{sku}` endpoint will be REPLACED/RENAMED to `GET /api/v1/catalog/products/skus` (or similar) accepting a list.

## Proposed Changes

### 1. Unified Product Service - Inventory Module
Refactor the reservation logic to handle atomic bulk reservations.

#### [MODIFY] [ReserveStockRequest.java](file:///d:/bunzo/inventory/product-service/src/main/java/com/quickcommerce/product/dto/ReserveStockRequest.java)
*   **Change:** Refactor to support a list of items.
*   **New Structure:**
    ```java
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class ReserveStockRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;

        @NotNull(message = "Customer ID is required")
        private Long customerId;

        @NotEmpty(message = "Items list cannot be empty")
        @Valid
        private List<StockItemRequest> items;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class StockItemRequest {
        @NotBlank(message = "SKU is required")
        private String sku;
        
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;
    }
    ```

#### [MODIFY] [InventoryController.java](file:///d:/bunzo/inventory/product-service/src/main/java/com/quickcommerce/product/controller/InventoryController.java)
*   Update `reserveStock` method to accept the new `ReserveStockRequest` structure.
*   Update logging to reflect bulk operation.

#### [MODIFY] [InventoryService.java](file:///d:/bunzo/inventory/product-service/src/main/java/com/quickcommerce/product/service/InventoryService.java)
*   Update `reserveStock` logic:
    *   **Transactional:** Ensure all items are reserved atomically.
    *   Iterate through items, check availability, and reserve.
    *   If any item fails, throw exception to trigger rollback.

---

### 2. Unified Product Service - Catalog Module
Modify product retrieval to support list of SKUs.

#### [MODIFY] [ProductController.java](file:///d:/bunzo/inventory/product-service/src/main/java/com/quickcommerce/product/catalog/controller/ProductController.java)
*   **Rename & Modify:** `getProductBySku` -> `getProductsBySkuList`
*   **Endpoint:** `GET /api/v1/catalog/products/skus` (Query param: `?sku=sku1,sku2` or `POST` body if list is long. *Decision: Use POST for robustness with large lists.*)
    *   **New Endpoint:** `POST /api/v1/catalog/products/skus`
    *   **Input:** `List<String> skus`
    *   **Output:** `Flux<ProductResponse>`
*   **Remove:** Old `GET /sku/{sku}` (Or keep as wrapper calling new service method).

#### [MODIFY] [CatalogService.java](file:///d:/bunzo/inventory/product-service/src/main/java/com/quickcommerce/product/catalog/service/CatalogService.java)
*   **Rename:** `getProductBySku` -> `getProductsBySkuList(List<String> skus)`
*   **Implementation:** Use `productRepository.findBySkuIn(skus)`.

---

## Verification Plan

### Automated Tests
1.  **Inventory Reservation (Bulk)**:
    *   Test: Reserve multiple items.
    *   Test: Partial failure triggers full rollback.
2.  **Product Retrieval (Bulk)**:
    *   Test: Fetch multiple SKUs. Verify all returned.
    *   Test: Fetch non-existent SKU (ignore or return partial).

### Manual Verification
*   Use `curl` to verify new `POST /catalog/products/skus` and `POST /inventory/reserve`.
