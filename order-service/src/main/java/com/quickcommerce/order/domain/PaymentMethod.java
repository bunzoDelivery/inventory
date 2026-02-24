package com.quickcommerce.order.domain;

public enum PaymentMethod {
    COD,
    AIRTEL_MONEY,
    MTN_MONEY;

    public boolean isCashOnDelivery() {
        return this == COD;
    }
}
