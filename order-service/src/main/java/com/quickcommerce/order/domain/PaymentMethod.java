package com.quickcommerce.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    COD("Cash on Delivery"),
    AIRTEL_MONEY("Airtel Money (USSD)"),
    MTN_MONEY("MTN Money (USSD)");

    private final String label;

    public boolean isCashOnDelivery() {
        return this == COD;
    }
}
