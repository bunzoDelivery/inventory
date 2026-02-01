package com.quickcommerce.product.catalog.controller;

import com.quickcommerce.product.catalog.dto.CreateProductRequest;
import com.quickcommerce.product.catalog.dto.ProductResponse;
import com.quickcommerce.product.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for product operations
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Slf4j
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
     * Get product by SKU
     */
    @GetMapping("/sku/{sku}")
    public Mono<ProductResponse> getProductBySku(@PathVariable String sku) {
        return catalogService.getProductBySku(sku);
    }

    /**
     * Get product by slug
     */
    @GetMapping("/slug/{slug}")
    public Mono<ProductResponse> getProductBySlug(@PathVariable String slug) {
        return catalogService.getProductBySlug(slug);
    }

    /**
     * Get products by category
     */
    @GetMapping("/category/{categoryId}")
    public Flux<ProductResponse> getProductsByCategory(@PathVariable Long categoryId) {
        return catalogService.getProductsByCategory(categoryId);
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
}
