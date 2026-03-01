package com.quickcommerce.search.mapper;

import com.quickcommerce.search.dto.CatalogProductDto;
import com.quickcommerce.search.model.ProductDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDocumentMapperTest {

    @Test
    void toProductDocument_imagesDirectCopy() {
        CatalogProductDto dto = new CatalogProductDto();
        dto.setId(1L);
        dto.setName("Milk");
        dto.setImages("[\"https://a.com/1.jpg\",\"https://a.com/2.jpg\"]");
        dto.setBasePrice(BigDecimal.valueOf(25.00));
        dto.setPackageSize("1L");
        dto.setSku("MILK-001");
        dto.setSlug("milk-1l");

        ProductDocument doc = ProductDocumentMapper.toProductDocument(dto);

        assertThat(doc.getImages()).isEqualTo("[\"https://a.com/1.jpg\",\"https://a.com/2.jpg\"]");
        assertThat(doc.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        assertThat(doc.getUnitText()).isEqualTo("1L");
        assertThat(doc.getSku()).isEqualTo("MILK-001");
        assertThat(doc.getSlug()).isEqualTo("milk-1l");
    }

    @Test
    void toProductDocument_imagesNull() {
        CatalogProductDto dto = new CatalogProductDto();
        dto.setId(2L);
        dto.setName("Bread");
        dto.setImages(null);
        dto.setBasePrice(BigDecimal.valueOf(1.49));

        ProductDocument doc = ProductDocumentMapper.toProductDocument(dto);

        assertThat(doc.getImages()).isNull();
        assertThat(doc.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1.49));
    }

    @Test
    void toProductDocument_nullDtoReturnsNull() {
        assertThat(ProductDocumentMapper.toProductDocument(null)).isNull();
    }
}
