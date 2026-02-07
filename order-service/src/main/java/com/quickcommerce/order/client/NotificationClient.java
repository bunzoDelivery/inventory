package com.quickcommerce.order.client;

import com.quickcommerce.order.domain.Order;
import reactor.core.publisher.Mono;

public interface NotificationClient {
    Mono<Void> sendOrderConfirmedEvent(Order order);
}
