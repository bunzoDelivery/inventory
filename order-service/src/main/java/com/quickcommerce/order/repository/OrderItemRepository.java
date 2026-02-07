package com.quickcommerce.order.repository;

import com.quickcommerce.order.domain.OrderItem;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OrderItemRepository extends R2dbcRepository<OrderItem, Long> {
    
    Flux<OrderItem> findByOrderId(Long orderId);
}
