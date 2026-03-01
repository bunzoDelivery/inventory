package com.quickcommerce.search.mapper;

import com.quickcommerce.search.dto.CatalogProductDto;
import com.quickcommerce.search.model.ProductDocument;

/**
 * Maps CatalogProductDto (catalog API contract) to ProductDocument (search index).
 */
public final class ProductDocumentMapper {

    private ProductDocumentMapper() {
    }

    /**
     * Convert CatalogProductDto to ProductDocument for indexing.
     * images: direct copy (full JSON string), no parsing.
     */
    public static ProductDocument toProductDocument(CatalogProductDto dto) {
        if (dto == null) {
            return null;
        }
        return ProductDocument.builder()
                .id(dto.getId())
                .sku(dto.getSku())
                .name(dto.getName())
                .brand(dto.getBrand())
                .description(dto.getDescription())
                .categoryId(dto.getCategoryId())
                .categoryName(null) // MVP: no category fetch in catalog response
                .barcode(dto.getBarcode())
                .isActive(dto.getIsActive())
                .price(dto.getBasePrice())
                .unitOfMeasure(dto.getUnitOfMeasure())
                .unitText(dto.getPackageSize())
                .slug(dto.getSlug())
                .images(dto.getImages())
                .build();
    }
}
