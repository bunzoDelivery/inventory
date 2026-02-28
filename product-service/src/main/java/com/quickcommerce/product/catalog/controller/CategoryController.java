package com.quickcommerce.product.catalog.controller;

import com.quickcommerce.product.catalog.dto.CategoryResponse;
import com.quickcommerce.product.catalog.dto.CategoryTreeResponse;
import com.quickcommerce.product.catalog.dto.CreateCategoryRequest;
import com.quickcommerce.product.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for category operations
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CatalogService catalogService;

    /**
     * Create a new category
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return catalogService.createCategory(request);
    }

    /**
     * Get category by ID
     */
    @GetMapping("/{id}")
    public Mono<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return catalogService.getCategoryById(id);
    }

    /**
     * Get category by slug
     */
    @GetMapping("/slug/{slug}")
    public Mono<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        return catalogService.getCategoryBySlug(slug);
    }

    /**
     * Get all root categories
     */
    @GetMapping("/root")
    public Flux<CategoryResponse> getRootCategories() {
        return catalogService.getRootCategories();
    }

    /**
     * Get child categories by parent ID
     */
    @GetMapping("/{parentId}/children")
    public Flux<CategoryResponse> getChildCategories(@PathVariable Long parentId) {
        return catalogService.getChildCategories(parentId);
    }

    /**
     * Get all active categories
     */
    @GetMapping
    public Flux<CategoryResponse> getAllActiveCategories() {
        return catalogService.getAllActiveCategories();
    }

    /**
     * Get hierarchical category tree
     * Returns categories organized in parent-child structure
     */
    @GetMapping("/tree")
    public Flux<CategoryTreeResponse> getCategoryTree() {
        return catalogService.getCategoryTree();
    }
}
