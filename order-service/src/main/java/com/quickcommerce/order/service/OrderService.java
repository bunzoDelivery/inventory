package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.domain.*;
import com.quickcommerce.order.dto.*;
import com.quickcommerce.order.exception.InsufficientStockException;
import com.quickcommerce.order.exception.InvalidOrderStateException;
import com.quickcommerce.order.exception.OrderNotFoundException;
import com.quickcommerce.order.exception.ServiceUnavailableException;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderItemRepository;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderEventRepository orderEventRepo;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final NotificationClient notificationClient;
    private final TransactionalOperator transactionalOperator;

    @Value("${order.delivery-fee-zmw:15.00}")
    private BigDecimal deliveryFee;

    // ─── Create Order ─────────────────────────────────────────────────────────

    public Mono<OrderResponse> createOrder(CreateOrderRequest req, String idempotencyKey) {
        log.info("Creating order for customer: {} store: {}", req.getCustomerId(), req.getStoreId());

        Mono<OrderResponse> createNew = createOrderInternal(req, idempotencyKey);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return orderRepo.findByIdempotencyKey(idempotencyKey)
                    .flatMap(existing -> buildFullResponse(existing))
                    .switchIfEmpty(createNew)
                    .onErrorResume(DataIntegrityViolationException.class, e -> {
                        log.warn("Duplicate idempotency key {}, returning existing order", idempotencyKey);
                        return orderRepo.findByIdempotencyKey(idempotencyKey)
                                .flatMap(existing -> buildFullResponse(existing));
                    });
        }

        return createNew;
    }

    private Mono<OrderResponse> createOrderInternal(CreateOrderRequest req, String idempotencyKey) {
        List<String> skus = req.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getSku)
                .toList();

        return catalogClient.getPrices(skus)
                .collectList()
                .flatMap(productPrices -> {
                    Map<String, BigDecimal> priceMap = productPrices.stream()
                            .collect(Collectors.toMap(ProductPriceResponse::getSku, ProductPriceResponse::getPrice));

                    // Validate all SKUs resolved and prices are sane
                    for (String sku : skus) {
                        if (!priceMap.containsKey(sku)) {
                            return Mono.error(new RuntimeException("Product not found: " + sku));
                        }
                        BigDecimal price = priceMap.get(sku);
                        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                            return Mono.error(new RuntimeException("Invalid price for product: " + sku));
                        }
                    }

                    boolean isCod = PaymentMethod.valueOf(req.getPaymentMethod()).isCashOnDelivery();
                    String orderUuid = UUID.randomUUID().toString();

                    List<OrderItem> items = req.getItems().stream()
                            .map(itemReq -> {
                                BigDecimal price = priceMap.get(itemReq.getSku());
                                return OrderItem.builder()
                                        .sku(itemReq.getSku())
                                        .qty(itemReq.getQuantity())
                                        .unitPrice(price)
                                        .subTotal(price.multiply(BigDecimal.valueOf(itemReq.getQuantity())))
                                        .build();
                            }).toList();

                    BigDecimal itemsTotal = items.stream()
                            .map(OrderItem::getSubTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    CreateOrderRequest.DeliveryRequest del = req.getDelivery();

                    Order order = Order.builder()
                            .orderUuid(orderUuid)
                            .storeId(req.getStoreId())
                            .customerId(req.getCustomerId().toString())
                            .paymentMethod(req.getPaymentMethod())
                            .currency("ZMW")
                            .totalAmount(itemsTotal)
                            .deliveryFee(deliveryFee)
                            .deliveryAddress(del.getAddress())
                            .deliveryLat(del.getLatitude() != null ? BigDecimal.valueOf(del.getLatitude()) : null)
                            .deliveryLng(del.getLongitude() != null ? BigDecimal.valueOf(del.getLongitude()) : null)
                            .deliveryPhone(del.getPhone())
                            .deliveryNotes(del.getNotes())
                            .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
                            .build();

                    if (isCod) {
                        order.setStatus(OrderStatus.CONFIRMED.name());
                        order.setPaymentStatus(PaymentStatus.COD_PENDING.name());
                    } else {
                        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
                        order.setPaymentStatus(PaymentStatus.PENDING.name());
                    }

                    ReserveStockRequest reserveReq = ReserveStockRequest.builder()
                            .orderId(orderUuid)
                            .customerId(req.getCustomerId())
                            .storeId(req.getStoreId())
                            .items(req.getItems().stream()
                                    .map(i -> new ReserveStockRequest.StockItemRequest(i.getSku(), i.getQuantity()))
                                    .toList())
                            .build();

                    return inventoryClient.reserveStock(reserveReq)
                            .collectList()
                            .flatMap(reservations -> {
                                if (reservations.isEmpty()) {
                                    return Mono.error(new InsufficientStockException("Insufficient stock"));
                                }

                                OrderStatus initialStatus = OrderStatus.valueOf(order.getStatus());

                                return orderRepo.save(order)
                                        .flatMap(savedOrder -> {
                                            items.forEach(item -> item.setOrderId(savedOrder.getId()));
                                            return orderItemRepo.saveAll(items)
                                                    .collectList()
                                                    .flatMap(savedItems ->
                                                            orderEventRepo.save(OrderEvent.created(savedOrder.getId(), initialStatus))
                                                                    .thenReturn(savedOrder));
                                        })
                                        .as(transactionalOperator::transactional)
                                        .flatMap(savedOrder -> {
                                            if (isCod) {
                                                return inventoryClient.confirmReservation(savedOrder.getOrderUuid())
                                                        .then(Mono.fromRunnable(() ->
                                                                notificationClient.sendOrderConfirmedEvent(savedOrder)
                                                                        .subscribe(v -> {}, e -> log.error(
                                                                                "Notification failed for order {}",
                                                                                savedOrder.getOrderUuid(), e))))
                                                        .then(buildFullResponse(savedOrder));
                                            }
                                            return buildFullResponse(savedOrder);
                                        })
                                        .onErrorResume(saveError -> {
                                            log.error("Failed to save order, releasing reservations for {}", orderUuid, saveError);
                                            return inventoryClient.cancelOrderReservations(orderUuid)
                                                    .then(Mono.error(saveError));
                                        });
                            })
                            .onErrorMap(e -> mapInventoryError(e));
                });
    }

    // ─── Mock Payment ──────────────────────────────────────────────────────────

    public Mono<OrderResponse> mockPayment(String orderUuid) {
        return orderRepo.findByOrderUuid(orderUuid)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                .flatMap(order -> {
                    if (order.paymentMethodEnum().isCashOnDelivery()) {
                        return Mono.error(new InvalidOrderStateException("COD orders cannot be paid online"));
                    }

                    OrderStatus current = order.orderStatus();
                    if (!current.canTransitionTo(OrderStatus.CONFIRMED)) {
                        return Mono.error(new InvalidOrderStateException(
                                "Cannot confirm payment for order in status: " + current));
                    }

                    OrderStatus previous = current;
                    order.setStatus(OrderStatus.CONFIRMED.name());
                    order.setPaymentStatus(PaymentStatus.PAID.name());

                    return orderRepo.save(order)
                            .flatMap(savedOrder ->
                                    orderEventRepo.save(OrderEvent.paymentReceived(savedOrder.getId(), savedOrder.paymentMethodEnum()))
                                            .then(inventoryClient.confirmReservation(savedOrder.getOrderUuid()))
                                            .then(Mono.fromRunnable(() ->
                                                    notificationClient.sendOrderConfirmedEvent(savedOrder)
                                                            .subscribe(v -> {}, e -> log.error(
                                                                    "Notification failed for order {}",
                                                                    savedOrder.getOrderUuid(), e))))
                                            .then(buildFullResponse(savedOrder)));
                });
    }

    // ─── Cancel Order ──────────────────────────────────────────────────────────

    public Mono<OrderResponse> cancelOrder(String orderUuid, String requestingCustomerId, String reason) {
        return orderRepo.findByOrderUuid(orderUuid)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                .flatMap(order -> {
                    if (!order.getCustomerId().equals(requestingCustomerId)) {
                        return Mono.error(new InvalidOrderStateException("Order does not belong to this customer"));
                    }

                    OrderStatus current = order.orderStatus();
                    if (!current.canTransitionTo(OrderStatus.CANCELLED)) {
                        return Mono.error(new InvalidOrderStateException(
                                "Order in status " + current + " cannot be cancelled"));
                    }

                    order.setStatus(OrderStatus.CANCELLED.name());
                    order.setCancelledReason(reason);

                    return orderRepo.save(order)
                            .flatMap(savedOrder -> {
                                Mono<Void> releaseStock = inventoryClient.cancelOrderReservations(orderUuid)
                                        .onErrorResume(e -> {
                                            log.error("Failed to release stock for cancelled order {}", orderUuid, e);
                                            return Mono.empty();
                                        });

                                return orderEventRepo.save(
                                                OrderEvent.cancelled(savedOrder.getId(), current, reason, "CUSTOMER"))
                                        .then(releaseStock)
                                        .then(buildFullResponse(savedOrder));
                            });
                })
                .doOnSuccess(r -> log.info("Order cancelled: {}", orderUuid));
    }

    // ─── Fulfillment Status Updates ────────────────────────────────────────────

    public Mono<OrderResponse> updateStatus(String orderUuid, String targetStatusStr, String actorId, String notes) {
        OrderStatus targetStatus = parseStatus(targetStatusStr);

        return orderRepo.findByOrderUuid(orderUuid)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                .flatMap(order -> {
                    OrderStatus current = order.orderStatus();

                    if (!current.canTransitionTo(targetStatus)) {
                        return Mono.error(new InvalidOrderStateException(
                                "Cannot transition from " + current + " to " + targetStatus));
                    }

                    order.setStatus(targetStatus.name());

                    return orderRepo.save(order)
                            .flatMap(savedOrder -> {
                                OrderEvent event = OrderEvent.statusChanged(
                                        savedOrder.getId(), current, targetStatus, actorId);
                                event.setNotes(notes);
                                return orderEventRepo.save(event)
                                        .then(buildFullResponse(savedOrder));
                            });
                });
    }

    // ─── Query Methods ─────────────────────────────────────────────────────────

    public Mono<OrderResponse> getOrder(String orderUuid) {
        return orderRepo.findByOrderUuid(orderUuid)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                .flatMap(this::buildFullResponse);
    }

    public Flux<OrderResponse> getCustomerOrders(String customerId, int page, int size) {
        return orderRepo.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size))
                .flatMap(this::buildFullResponse);
    }

    public Flux<OrderResponse> getStoreOrders(Long storeId, String status, int page, int size) {
        if (status != null && !status.isBlank()) {
            return orderRepo.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, status.toUpperCase(), PageRequest.of(page, size))
                    .flatMap(this::buildFullResponse);
        }
        return orderRepo.findByStoreIdOrderByCreatedAtDesc(storeId, PageRequest.of(page, size))
                .flatMap(this::buildFullResponse);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Mono<OrderResponse> buildFullResponse(Order order) {
        return orderItemRepo.findByOrderId(order.getId())
                .collectList()
                .map(items -> mapToResponse(order, items));
    }

    private OrderResponse mapToResponse(Order order, List<OrderItem> items) {
        List<OrderResponse.OrderItemResponse> itemResponses = items.stream()
                .map(i -> new OrderResponse.OrderItemResponse(
                        i.getSku(), i.getQty(), i.getUnitPrice(), i.getSubTotal()))
                .toList();

        String message = switch (OrderStatus.valueOf(order.getStatus())) {
            case CONFIRMED        -> "Order confirmed";
            case PENDING_PAYMENT  -> "Please proceed to payment";
            case PACKING          -> "Your order is being packed";
            case OUT_FOR_DELIVERY -> "Your order is out for delivery";
            case DELIVERED        -> "Order delivered";
            case CANCELLED        -> "Order cancelled";
        };

        BigDecimal itemsTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal fee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;

        OrderResponse.DeliveryInfo deliveryInfo = null;
        if (order.getDeliveryAddress() != null) {
            deliveryInfo = OrderResponse.DeliveryInfo.builder()
                    .address(order.getDeliveryAddress())
                    .latitude(order.getDeliveryLat() != null ? order.getDeliveryLat().doubleValue() : null)
                    .longitude(order.getDeliveryLng() != null ? order.getDeliveryLng().doubleValue() : null)
                    .phone(order.getDeliveryPhone())
                    .notes(order.getDeliveryNotes())
                    .build();
        }

        return OrderResponse.builder()
                .orderId(order.getOrderUuid())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .message(message)
                .itemsTotal(itemsTotal)
                .deliveryFee(fee)
                .grandTotal(itemsTotal.add(fee))
                .currency(order.getCurrency())
                .delivery(deliveryInfo)
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderStatus parseStatus(String statusStr) {
        try {
            return OrderStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderStateException("Unknown status: " + statusStr);
        }
    }

    private Throwable mapInventoryError(Throwable e) {
        log.error("Inventory operation failed", e);
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcEx) {
            int status = wcEx.getStatusCode().value();
            if (status >= 500 || status == 408) {
                return new ServiceUnavailableException("Inventory service unavailable", e);
            }
        }
        String msg = e.getMessage();
        if (msg != null && (msg.contains("Connection") || msg.contains("timeout") || msg.contains("Timeout"))) {
            return new ServiceUnavailableException("Inventory service unavailable", e);
        }
        return new InsufficientStockException("Insufficient stock or product not found", e);
    }
}
