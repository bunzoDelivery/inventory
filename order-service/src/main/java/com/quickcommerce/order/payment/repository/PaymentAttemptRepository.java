package com.quickcommerce.order.payment.repository;

import com.quickcommerce.order.payment.domain.PaymentAttempt;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PaymentAttemptRepository extends ReactiveCrudRepository<PaymentAttempt, Long> {

    Mono<PaymentAttempt> findByAirtelRef(String airtelRef);

    Mono<PaymentAttempt> findByOrderUuid(String orderUuid);
}
