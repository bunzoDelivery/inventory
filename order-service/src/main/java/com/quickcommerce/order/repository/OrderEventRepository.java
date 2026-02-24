package com.quickcommerce.order.repository;

import com.quickcommerce.order.domain.OrderEvent;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OrderEventRepository extends R2dbcRepository<OrderEvent, Long> {

    Flux<OrderEvent> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
