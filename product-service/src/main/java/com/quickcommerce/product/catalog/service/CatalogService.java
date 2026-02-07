package com.quickcommerce.product.catalog.service;

import com.quickcommerce.product.catalog.domain.Category;
import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.*;
import com.quickcommerce.product.catalog.repository.CategoryRepository;
import com.quickcommerce.product.catalog.repository.ProductRepository;
import com.quickcommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Service for managing catalog operations (categories and products)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // ============ Category Operations ============

    /**
     * Create a new category
     */
    @Transactional
    // @CacheEvict(value = "categories:all", allEntries = true) // Disabled for MVP
    public Mono<CategoryResponse> createCategory(CreateCategoryRequest request) {
        log.info("Creating category: {}", request.getName());

        // Check if slug already exists
        return categoryRepository.findBySlug(request.getSlug())
                .flatMap(existing -> Mono.<Category>error(
                        new IllegalArgumentException("Category with slug '" + request.getSlug() + "' already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    Category category = new Category();
                    category.setName(request.getName());
                    category.setDescription(request.getDescription());
                    category.setParentId(request.getParentId());
                    category.setSlug(request.getSlug());
                    category.setDisplayOrder(request.getDisplayOrder());
                    category.setIsActive(request.getIsActive());
                    category.setImageUrl(request.getImageUrl());
                    category.setCreatedAt(LocalDateTime.now());
                    category.setUpdatedAt(LocalDateTime.now());

                    return categoryRepository.save(category);
                }))
                .map(CategoryResponse::fromDomain);
    }

    /**
     * Get category by ID
     */
    @Cacheable(value = "categories", key = "#id")
    public Mono<CategoryResponse> getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category", id.toString())))
                .map(CategoryResponse::fromDomain);
    }

    /**
     * Get category by slug
     */
    public Mono<CategoryResponse> getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category", slug)))
                .map(CategoryResponse::fromDomain);
    }

    /**
     * Get all root categories
     */
    public Flux<CategoryResponse> getRootCategories() {
        return categoryRepository.findRootCategories()
                .map(CategoryResponse::fromDomain);
    }

    /**
     * Get child categories by parent ID
     */
    public Flux<CategoryResponse> getChildCategories(Long parentId) {
        return categoryRepository.findByParentId(parentId)
                .map(CategoryResponse::fromDomain);
    }

    /**
     * Get all active categories
     */
    // @Cacheable(value = "categories:all") // Disabled for MVP
    public Flux<CategoryResponse> getAllActiveCategories() {
        return categoryRepository.findAllActive()
                .map(CategoryResponse::fromDomain);
    }

    // ============ Product Operations ============

    /**
     * Create a new product
     */
    @Transactional
    public Mono<ProductResponse> createProduct(CreateProductRequest request) {
        log.info("Creating product: {} (SKU: {})", request.getName(), request.getSku());

        // Check if SKU already exists
        return productRepository.existsBySku(request.getSku())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Product with SKU '" + request.getSku() + "' already exists"));
                    }

                    // Verify category exists
                    return categoryRepository.findById(request.getCategoryId())
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                                    "Category", request.getCategoryId().toString())))
                            .then(Mono.defer(() -> {
                                Product product = new Product();
                                product.setSku(request.getSku());
                                product.setName(request.getName());
                                product.setDescription(request.getDescription());
                                product.setShortDescription(request.getShortDescription());
                                product.setCategoryId(request.getCategoryId());
                                product.setBrand(request.getBrand());
                                product.setBasePrice(request.getBasePrice());
                                product.setUnitOfMeasure(request.getUnitOfMeasure());
                                product.setPackageSize(request.getPackageSize());
                                product.setImages(request.getImages());
                                product.setTags(request.getTags());
                                product.setIsActive(request.getIsActive());
                                product.setIsAvailable(request.getIsAvailable());
                                product.setSlug(request.getSlug());
                                product.setNutritionalInfo(request.getNutritionalInfo());
                                product.setWeightGrams(request.getWeightGrams());
                                product.setBarcode(request.getBarcode());
                                product.setCreatedAt(LocalDateTime.now());
                                product.setUpdatedAt(LocalDateTime.now());

                                return productRepository.save(product);
                            }));
                })
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get product by ID (cached for 5 minutes)
     */
    @Cacheable(value = "products", key = "#id")
    public Mono<ProductResponse> getProductById(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", id.toString())))
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get products by SKU list (Bulk)
     * Robust implementation with logging and distinctive filtering
     */
    public Flux<ProductResponse> getProductsBySkuList(java.util.List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            log.warn("Empty SKU list requested");
            return Flux.empty();
        }

        // De-duplicate SKUs to prevent redundant DB load
        java.util.List<String> distinctSkus = skus.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        if (distinctSkus.isEmpty()) {
            return Flux.empty();
        }

        log.info("Fetching {} products (requested: {})", distinctSkus.size(), skus.size());

        return productRepository.findBySkuIn(distinctSkus)
                .map(ProductResponse::fromDomain)
                .doOnComplete(() -> log.debug("Bulk product retrieval completed"))
                .doOnError(e -> log.error("Error fetching products by SKUs: {}", e.getMessage()));
    }

    /**
     * Get product by slug
     */
    public Mono<ProductResponse> getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", slug)))
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get products by category
     */
    public Flux<ProductResponse> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId)
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get products by brand
     */
    public Flux<ProductResponse> getProductsByBrand(String brand) {
        return productRepository.findByBrand(brand)
                .map(ProductResponse::fromDomain);
    }

    /**
     * Search products by term (optimized with smart query selection)
     */
    public Flux<ProductResponse> searchProducts(String searchTerm, Integer limit) {
        int searchLimit = (limit != null && limit > 0) ? limit : 50;

        // Use smart search that chooses between LIKE and FULLTEXT based on query length
        return productRepository.searchProductsSmart(searchTerm, searchLimit)
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get all products (admin/sync use)
     */
    public Flux<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get all available products
     */
    public Flux<ProductResponse> getAllAvailableProducts() {
        return productRepository.findAllAvailable()
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get bestseller products
     */
    public Flux<ProductResponse> getBestsellers(Integer limit) {
        int searchLimit = (limit != null && limit > 0) ? limit : 20;
        return productRepository.findBestsellers(searchLimit)
                .map(ProductResponse::fromDomain);
    }

    /**
     * Get products by price range
     */
    public Flux<ProductResponse> getProductsByPriceRange(Double minPrice, Double maxPrice) {
        return productRepository.findByPriceRange(minPrice, maxPrice)
                .map(ProductResponse::fromDomain);
    }
}
