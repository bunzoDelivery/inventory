package com.quickcommerce.order.payment.repository;

import com.quickcommerce.order.payment.domain.PaymentAttempt;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface PaymentAttemptRepository extends ReactiveCrudRepository<PaymentAttempt, Long> {

    Mono<PaymentAttempt> findByGatewayRef(String gatewayRef);

    Mono<PaymentAttempt> findByOrderUuid(String orderUuid);

    /**
     * Returns INITIATED attempts whose push was sent before {@code cutoff} but no webhook
     * has resolved them yet. Used by {@link com.quickcommerce.order.payment.scheduler.GenericPaymentFailsafeScheduler}
     * to poll provider status directly.
     *
     * The index idx_pa_status_initiated_at (added in V14) keeps this query fast.
     */
    @Query("SELECT * FROM payment_attempts " +
            "WHERE status = 'INITIATED' " +
            "AND initiated_at < :cutoff " +
            "ORDER BY initiated_at ASC")
    Flux<PaymentAttempt> findPendingOlderThan(LocalDateTime cutoff);
}
