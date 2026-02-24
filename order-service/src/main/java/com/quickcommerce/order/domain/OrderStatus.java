package com.quickcommerce.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

    PENDING_PAYMENT,
    CONFIRMED,
    PACKING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    private static final java.util.Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS =
            new java.util.EnumMap<>(OrderStatus.class);

    static {
        VALID_TRANSITIONS.put(PENDING_PAYMENT, EnumSet.of(CONFIRMED, CANCELLED));
        VALID_TRANSITIONS.put(CONFIRMED,       EnumSet.of(PACKING, CANCELLED));
        VALID_TRANSITIONS.put(PACKING,         EnumSet.of(OUT_FOR_DELIVERY, CANCELLED));
        VALID_TRANSITIONS.put(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, CANCELLED));
        VALID_TRANSITIONS.put(DELIVERED,       EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS.put(CANCELLED,       EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(next);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
