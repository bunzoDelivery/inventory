package com.quickcommerce.search.entity;

import com.quickcommerce.search.entity.converter.JsonAttributeConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SearchSetting {

    @Id
    @Column(name = "setting_key", nullable = false, unique = true)
    private String key;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "setting_value", columnDefinition = "JSON", nullable = false)
    private Object value;

    @Column(name = "description")
    private String description;

    @Version
    private Integer version;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
