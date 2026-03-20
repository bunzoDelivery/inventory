package com.quickcommerce.search.mapper;

import com.quickcommerce.search.dto.CatalogProductDto;
import com.quickcommerce.search.model.ProductDocument;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps CatalogProductDto (catalog API contract) to ProductDocument (search index).
 */
public final class ProductDocumentMapper {

    private ProductDocumentMapper() {
    }

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
                .categoryName(dto.getCategoryName())
                .keywords(parseKeywords(dto.getSearchKeywords()))
                .barcode(dto.getBarcode())
                .isActive(dto.getIsActive())
                .price(dto.getBasePrice())
                .unitOfMeasure(dto.getUnitOfMeasure())
                .unitText(dto.getPackageSize())
                .slug(dto.getSlug())
                .images(dto.getImages())
                .searchPriority(dto.getSearchPriority() != null ? dto.getSearchPriority() : 0)
                .isBestseller(dto.getIsBestseller() != null ? dto.getIsBestseller() : false)
                .orderCount(dto.getOrderCount() != null ? dto.getOrderCount() : 0)
                .build();
    }

    private static List<String> parseKeywords(String searchKeywords) {
        if (searchKeywords == null || searchKeywords.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(searchKeywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
