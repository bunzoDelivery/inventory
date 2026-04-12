package com.quickcommerce.product.catalog.controller;

import com.quickcommerce.common.dto.VariantDto;
import com.quickcommerce.product.catalog.dto.CreateProductRequest;
import com.quickcommerce.product.catalog.dto.GroupSummary;
import com.quickcommerce.product.catalog.dto.PagedProductResponse;
import com.quickcommerce.product.catalog.dto.ProductResponse;
import com.quickcommerce.product.catalog.dto.ProductSortOption;
import com.quickcommerce.product.catalog.dto.VariantGroupBatchRequest;
import com.quickcommerce.product.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller for product operations
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProductController {

    private final CatalogService catalogService;

    /**
     * Create a new product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return catalogService.createProduct(request);
    }

    /**
     * Get product by ID
     */
    @GetMapping("/{id}")
    public Mono<ProductResponse> getProductById(@PathVariable Long id) {
        return catalogService.getProductById(id);
    }

    /**
     * Get products by SKU list (Bulk)
     * POST /api/v1/catalog/products/skus
     */
    /**
     * Get products by SKU list (Bulk)
     * POST /api/v1/catalog/products/skus
     */
    @PostMapping("/skus")
    public Flux<ProductResponse> getProductsBySkuList(
            @Valid @RequestBody com.quickcommerce.product.catalog.dto.ProductListRequest request) {
        return catalogService.getProductsBySkuList(request.getSkus());
    }

    /**
     * Get product by slug
     */
    @GetMapping("/slug/{slug}")
    public Mono<ProductResponse> getProductBySlug(@PathVariable String slug) {
        return catalogService.getProductBySlug(slug);
    }

    /**
     * Get products by category with pagination, optional price sort and brand filter.
     * sortBy: PRICE_ASC | PRICE_DESC (optional, default: name order)
     * brand: exact brand name filter (optional)
     */
    @GetMapping("/category/{categoryId}")
    public Mono<PagedProductResponse> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(required = false) ProductSortOption sortBy,
            @RequestParam(required = false) String brand) {
        return catalogService.getProductsByCategory(categoryId, pageNum, pageSize, sortBy, brand);
    }

    /**
     * Get products by brand
     */
    @GetMapping("/brand/{brand}")
    public Flux<ProductResponse> getProductsByBrand(@PathVariable String brand) {
        return catalogService.getProductsByBrand(brand);
    }

    /**
     * Search products
     */
    @GetMapping("/search")
    public Flux<ProductResponse> searchProducts(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return catalogService.searchProducts(q, limit);
    }

    /**
     * Get all available products
     */
    @GetMapping
    public Flux<ProductResponse> getAllAvailableProducts() {
        return catalogService.getAllAvailableProducts();
    }

    /**
     * Get bestseller products
     */
    @GetMapping("/bestsellers")
    public Flux<ProductResponse> getBestsellers(
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return catalogService.getBestsellers(limit);
    }

    /**
     * Get all products (including inactive)
     */
    @GetMapping("/all")
    public Flux<ProductResponse> getAllProducts() {
        return catalogService.getAllProducts();
    }

    /**
     * Get products by price range
     */
    @GetMapping("/price-range")
    public Flux<ProductResponse> getProductsByPriceRange(
            @RequestParam Double minPrice,
            @RequestParam Double maxPrice) {
        return catalogService.getProductsByPriceRange(minPrice, maxPrice);
    }

    // ============ Variant Group Endpoints ============

    /**
     * Batch fetch variant groups for the bottom sheet.
     * Mobile calls this in the background after receiving a listing page, caching the
     * results so that tapping +ADD opens the bottom sheet instantly.
     *
     * POST /api/v1/catalog/products/groups/batch
     * Body: { "groupIds": ["amul-taaza-milk", "maggi-noodles"] }
     * → { "amul-taaza-milk": [ { productId, sku, size, price, inStock }, ... ], ... }
     *
     * Max 50 group IDs per request (one listing page worth).
     */
    @PostMapping("/groups/batch")
    public Mono<Map<String, List<VariantDto>>> getVariantGroups(
            @Valid @RequestBody VariantGroupBatchRequest request) {
        return catalogService.getVariantGroups(request.getGroupIds());
    }

    /**
     * List all distinct group IDs with their variant count.
     * Admin-facing: use this to verify grouping and copy-paste IDs when creating products manually.
     *
     * GET /api/v1/catalog/products/groups
     * → [ { "groupId": "amul-taaza-milk", "variantCount": 3 }, ... ]
     */
    @GetMapping("/groups")
    public Flux<GroupSummary> getAllGroups() {
        return catalogService.getAllGroupSummaries();
    }
}
