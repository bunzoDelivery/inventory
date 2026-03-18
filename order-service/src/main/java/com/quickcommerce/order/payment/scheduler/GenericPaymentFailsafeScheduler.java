package com.quickcommerce.order.payment.scheduler;

import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.gateway.GatewayStatusOutcome;
import com.quickcommerce.order.payment.gateway.PaymentGatewayRouter;
import com.quickcommerce.order.payment.repository.PaymentAttemptRepository;
import com.quickcommerce.order.payment.service.PaymentService;
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
 * <p>Scenario: customer paid, but the payment provider's webhook never reached us
 * (network issue, server restart, provider outage). Without this scheduler, the order
 * would stay PENDING_PAYMENT forever and eventually be cancelled by the cleanup job —
 * even though the customer actually paid.
 *
 * <p>Every {@code payment.failsafe-interval-ms} (default 55s), this job:
 * <ol>
 *   <li>Queries {@code payment_attempts} for INITIATED rows older than {@code payment.failsafe-cutoff-seconds}</li>
 *   <li>For each attempt, reads {@code gateway_used} and routes the status check to the
 *       correct {@link com.quickcommerce.order.payment.gateway.PaymentGateway} adapter.</li>
 *   <li>SUCCESS  → confirms the order (same path as webhook success)</li>
 *   <li>FAILED   → cancels the order (same path as webhook failure)</li>
 *   <li>PENDING  → leaves it for the next scheduler run</li>
 * </ol>
 *
 * <p><b>Gateway routing:</b> uses {@link PaymentGatewayRouter#resolve(com.quickcommerce.order.payment.gateway.GatewayName)}
 * so it always calls the gateway that originally processed the attempt, regardless of which
 * gateway is currently active for new payments. This is critical during migration periods
 * where old Airtel attempts coexist with new PawaPay ones.
 *
 * <p><b>Race safety:</b> {@link PaymentService#processPaymentSuccess} and
 * {@link PaymentService#processPaymentFailure} use {@code @Version} optimistic locking.
 * If a webhook arrives while the scheduler is running, the second writer gets an
 * {@code OptimisticLockingFailureException} and no-ops cleanly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericPaymentFailsafeScheduler {

    private final PaymentAttemptRepository paymentAttemptRepo;
    private final OrderRepository orderRepo;
    private final PaymentGatewayRouter gatewayRouter;
    private final PaymentService paymentService;

    @Value("${payment.failsafe-cutoff-seconds:60}")
    private int failsafeCutoffSeconds;

    @Scheduled(fixedRateString = "${payment.failsafe-interval-ms:55000}")
    public void checkStuckPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(failsafeCutoffSeconds);

        paymentAttemptRepo.findPendingOlderThan(cutoff)
                .take(20) // Safety cap: don't hammer provider APIs in one run
                .flatMap(attempt -> checkAttempt(attempt)
                        .onErrorResume(e -> {
                            log.error("Failsafe: error checking attempt id={}, orderUuid={}, gateway={}",
                                    attempt.getId(), attempt.getOrderUuid(), attempt.getGatewayUsed(), e);
                            return Mono.empty();
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("GenericPaymentFailsafeScheduler error", e))
                .onErrorResume(e -> Flux.empty())
                .subscribe();
    }

    private Mono<Void> checkAttempt(PaymentAttempt attempt) {
        if (attempt.getGatewayUsed() == null || attempt.getGatewayRef() == null) {
            log.warn("Failsafe: skipping attempt id={} — missing gateway_used or gateway_ref", attempt.getId());
            return Mono.empty();
        }

        log.info("Failsafe: checking status for orderUuid={}, gateway={}, gatewayRef={}",
                attempt.getOrderUuid(), attempt.getGatewayUsed(), attempt.getGatewayRef());

        return gatewayRouter.resolve(attempt.getGatewayUsed())
                .checkPaymentStatus(attempt.getGatewayRef())
                .flatMap(statusResp -> {
                    GatewayStatusOutcome outcome = statusResp.getOutcome();
                    log.info("Failsafe: orderUuid={}, gateway={}, rawStatus={}, outcome={}",
                            attempt.getOrderUuid(), attempt.getGatewayUsed(),
                            statusResp.getRawStatus(), outcome);

                    return switch (outcome) {
                        case SUCCESS -> orderRepo.findByOrderUuid(attempt.getOrderUuid())
                                .flatMap(order -> {
                                    attempt.setStatus(PaymentAttemptStatus.SUCCESS);
                                    attempt.setResolvedAt(LocalDateTime.now());
                                    return paymentService.processPaymentSuccess(order, attempt);
                                });

                        case FAILED -> orderRepo.findByOrderUuid(attempt.getOrderUuid())
                                .flatMap(order -> {
                                    attempt.setStatus(PaymentAttemptStatus.FAILED);
                                    attempt.setResolvedAt(LocalDateTime.now());
                                    return paymentService.processPaymentFailure(order, attempt,
                                            attempt.getGatewayUsed().name() + " status: " +
                                            statusResp.getRawStatus() + " (detected by failsafe)");
                                });

                        case PENDING -> {
                            log.debug("Failsafe: orderUuid={} still in-progress, skipping",
                                    attempt.getOrderUuid());
                            yield Mono.empty();
                        }
                    };
                });
    }
}
