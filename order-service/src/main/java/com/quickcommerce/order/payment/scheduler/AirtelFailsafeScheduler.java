package com.quickcommerce.order.payment.scheduler;

import com.quickcommerce.order.domain.OrderStatus;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.repository.PaymentAttemptRepository;
import com.quickcommerce.order.payment.service.PaymentService;
import com.quickcommerce.order.payment.client.AirtelMoneyClient;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * Failsafe for "no webhook received" scenarios.
 *
 * Scenario: Customer paid, but Airtel's webhook never reached us (network issue, server restart).
 * Without this scheduler, the order would stay PENDING_PAYMENT forever until the cleanup job
 * cancels it — even though the customer paid.
 *
 * Every 55 seconds, this job:
 * 1. Finds PENDING_PAYMENT orders that have an airtelTransactionId (push was sent) and are >60s old
 * 2. Calls Airtel's status check API for each
 * 3. TS  → confirms the payment (same as webhook success path)
 * 4. TF/TA/TE → cancels the order (same as webhook failure path)
 * 5. TIP/DP_INITIATED → still in progress, leaves it for the next run
 *
 * Race safety: processPaymentSuccess/Failure use @Version optimistic locking on Order.
 * If a webhook arrives at the same time, the second writer gets OptimisticLockingFailureException
 * and no-ops cleanly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirtelFailsafeScheduler {

    private final OrderRepository orderRepo;
    private final PaymentAttemptRepository paymentAttemptRepo;
    private final AirtelMoneyClient airtelClient;
    private final PaymentService paymentService;

    @Value("${airtel.failsafe-cutoff-seconds:60}")
    private int failsafeCutoffSeconds;

    @Scheduled(fixedRateString = "${airtel.failsafe-interval-ms:55000}")
    public void checkStuckOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(failsafeCutoffSeconds);

        orderRepo.findStuckAirtelOrders(OrderStatus.PENDING_PAYMENT.name(), cutoff)
                .take(20) // Safety cap: don't overwhelm Airtel API in one run
                .flatMap(order -> {
                    String txId = order.getAirtelTransactionId();
                    if (txId == null || txId.isBlank()) {
                        return Mono.empty();
                    }

                    log.info("Failsafe: checking Airtel status for order={}, txId={}",
                            order.getOrderUuid(), txId);

                    return airtelClient.checkPaymentStatus(txId)
                            .flatMap(statusResp -> {
                                String status = statusResp.getStatus();

                                return switch (status) {
                                    case "TS" -> {
                                        log.info("Failsafe: order={} — Airtel confirmed SUCCESS",
                                                order.getOrderUuid());
                                        yield resolveAttempt(order, txId)
                                                .flatMap(a -> paymentService.processPaymentSuccess(order, a));
                                    }
                                    case "TF", "TA", "TE" -> {
                                        log.info("Failsafe: order={} — Airtel reported FAILED ({})",
                                                order.getOrderUuid(), status);
                                        yield resolveAttempt(order, txId)
                                                .flatMap(a -> paymentService.processPaymentFailure(order, a,
                                                        "Airtel status: " + status + " (detected by failsafe)"));
                                    }
                                    default -> {
                                        // TIP / DP_INITIATED — still in progress, leave it
                                        log.debug("Failsafe: order={} still in-progress ({}), skipping",
                                                order.getOrderUuid(), status);
                                        yield Mono.empty();
                                    }
                                };
                            })
                            .onErrorResume(e -> {
                                log.error("Failsafe: error checking order={}", order.getOrderUuid(), e);
                                return Mono.empty();
                            });
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("AirtelFailsafeScheduler error", e))
                .onErrorResume(e -> Flux.empty())
                .subscribe();
    }

    /**
     * Returns the persisted PaymentAttempt for this txId if one exists,
     * or a transient synthetic one if none was found (e.g. app restarted mid-flow).
     *
     * The synthetic attempt has a null id — processPaymentSuccess/Failure will save it
     * inside their transactional boundary, resulting in a new INSERT. This is intentional:
     * if the real attempt row never made it to DB, a new audit row is better than silence.
     */
    private Mono<PaymentAttempt> resolveAttempt(com.quickcommerce.order.domain.Order order, String txId) {
        return paymentAttemptRepo.findByAirtelRef(txId)
                .defaultIfEmpty(PaymentAttempt.builder()
                        .orderUuid(order.getOrderUuid())
                        .paymentPhone(order.getPaymentPhone())
                        .airtelRef(txId)
                        .status(PaymentAttemptStatus.INITIATED)
                        .initiatedAt(order.getUpdatedAt())
                        .build());
    }
}
