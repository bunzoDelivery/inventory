package com.quickcommerce.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Domain entity representing stock movements for audit trail
 * Tracks all inventory changes with reference information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("inventory_movements")
public class StockMovement {

    @Id
    private Long id;

    @Column("inventory_item_id")
    private Long inventoryItemId;

    @Column("movement_type")
    private MovementType movementType;

    @Column("quantity")
    private Integer quantity;

    @Column("reference_type")
    private ReferenceType referenceType;

    @Column("reference_id")
    private String referenceId;

    @Column("reason")
    private String reason;

    @Column("created_by")
    private String createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * Types of stock movements
     */
    public enum MovementType {
        INBOUND("Stock received"),
        OUTBOUND("Stock sold/shipped"),
        RESERVE("Stock reserved for order"),
        UNRESERVE("Stock reservation released"),
        ADJUSTMENT("Manual stock adjustment");

        private final String description;

        MovementType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Reference types for tracking movement sources
     */
    public enum ReferenceType {
        PURCHASE("Purchase order"),
        SALE("Customer order"),
        RETURN("Product return"),
        ADJUSTMENT("Manual adjustment"),
        RESERVATION("Stock reservation");

        private final String description;

        ReferenceType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
