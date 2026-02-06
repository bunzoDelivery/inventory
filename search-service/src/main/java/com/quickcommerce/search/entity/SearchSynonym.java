package com.quickcommerce.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity for search synonyms
 * Stores synonym mappings for search query expansion
 */
@Table("search_synonyms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSynonym {

    @Id
    private Long id;

    @Column("term")
    private String term;

    /**
     * JSON string containing array of synonyms
     * Converted to/from List<String> in service layer
     */
    @Column("synonyms")
    private String synonymsJson;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;
}
