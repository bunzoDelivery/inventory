package com.quickcommerce.order.repository;

import com.quickcommerce.order.domain.Order;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, Long> {
    
    Mono<Order> findByOrderUuid(String orderUuid);

    Mono<Order> findByIdempotencyKey(String idempotencyKey);
    
    Flux<Order> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
}
