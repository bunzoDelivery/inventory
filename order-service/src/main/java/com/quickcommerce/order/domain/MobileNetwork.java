package com.quickcommerce.order.domain;

/**
 * The mobile money network the customer is paying from.
 * PawaPay routes to both; Airtel Direct only supports AIRTEL.
 */
public enum MobileNetwork {
    AIRTEL,
    MTN
}
