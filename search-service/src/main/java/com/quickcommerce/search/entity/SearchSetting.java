package com.quickcommerce.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity for search settings
 * Stores Meilisearch configuration as JSON
 */
@Table("search_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSetting {

    @Id
    @Column("setting_key")
    private String key;

    /**
     * JSON string containing setting value
     * Parsed to appropriate type in service layer
     */
    @Column("setting_value")
    private String valueJson;

    @Column("description")
    private String description;

    @Version
    @Column("version")
    private Integer version;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;
}
