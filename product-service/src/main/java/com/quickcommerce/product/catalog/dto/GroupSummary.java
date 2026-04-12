package com.quickcommerce.product.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Projection for the admin-facing group listing endpoint.
 * Returns each distinct group_id with the number of active variants in that group.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupSummary {

    private String groupId;

    private Long variantCount;
}
