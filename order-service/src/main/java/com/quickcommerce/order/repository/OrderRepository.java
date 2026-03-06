package com.quickcommerce.order.repository;

import com.quickcommerce.order.domain.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, Long> {

    Mono<Order> findByOrderUuid(String orderUuid);

    Mono<Order> findByAirtelTransactionId(String airtelTransactionId);

    Mono<Order> findByIdempotencyKey(String idempotencyKey);

    Flux<Order> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);

    Flux<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    Flux<Order> findByStoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    Flux<Order> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, String status, Pageable pageable);

    /**
     * Finds Airtel Money orders that:
     * - Are in PENDING_PAYMENT status
     * - Have an airtelTransactionId (push was sent)
     * - Were last updated before the cutoff (no webhook received yet)
     * Used by AirtelFailsafeScheduler.
     */
    @Query("SELECT * FROM customer_orders " +
            "WHERE status = :status " +
            "AND airtel_transaction_id IS NOT NULL " +
            "AND updated_at < :cutoff " +
            "ORDER BY updated_at ASC")
    Flux<Order> findStuckAirtelOrders(String status, LocalDateTime cutoff);
}
