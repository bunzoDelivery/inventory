package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.exception.InsufficientStockException;
import com.quickcommerce.order.exception.InvalidOrderStateException;
import com.quickcommerce.order.exception.OrderNotFoundException;
import com.quickcommerce.order.exception.ServiceUnavailableException;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.domain.OrderItem;
import com.quickcommerce.order.dto.*;
import com.quickcommerce.order.repository.OrderItemRepository;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
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
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final NotificationClient notificationClient;
    private final TransactionalOperator transactionalOperator;

    public Mono<OrderResponse> createOrder(CreateOrderRequest req, String idempotencyKey) {
        log.info("Creating order for customer: {}", req.getCustomerId());

        Mono<OrderResponse> createNew = createOrderInternal(req, idempotencyKey);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return orderRepo.findByIdempotencyKey(idempotencyKey)
                    .flatMap(existingOrder -> orderItemRepo.findByOrderId(existingOrder.getId()).collectList()
                            .map(items -> mapToResponse(existingOrder, items)))
                    .switchIfEmpty(createNew);
        }

        return createNew;
    }

    private Mono<OrderResponse> createOrderInternal(CreateOrderRequest req, String idempotencyKey) {
        List<String> skus = req.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getSku)
                .toList();

        // 1. Fetch Prices & Validate
        return catalogClient.getPrices(skus)
                .collectList()
                .flatMap(productPrices -> {
                    Map<String, BigDecimal> priceMap = productPrices.stream()
                            .collect(Collectors.toMap(ProductPriceResponse::getSku, ProductPriceResponse::getPrice));

                    for (String sku : skus) {
                        if (!priceMap.containsKey(sku)) {
                            return Mono.error(new RuntimeException("Product not found: " + sku));
                        }
                    }

                    String orderUuid = UUID.randomUUID().toString();
                    boolean isCod = "COD".equalsIgnoreCase(req.getPaymentMethod());

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

                    BigDecimal totalAmount = items.stream()
                            .map(OrderItem::getSubTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Order order = Order.builder()
                            .orderUuid(orderUuid)
                            .storeId(req.getStoreId())
                            .customerId(req.getCustomerId().toString())
                            .paymentMethod(req.getPaymentMethod())
                            .currency("ZMW")
                            .totalAmount(totalAmount)
                            .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
                            .build();

                    if (isCod) {
                        order.setStatus("CONFIRMED");
                        order.setPaymentStatus("COD_PENDING");
                    } else {
                        order.setStatus("PENDING_PAYMENT");
                        order.setPaymentStatus("PENDING");
                    }

                    ReserveStockRequest reserveReq = ReserveStockRequest.builder()
                            .orderId(orderUuid)
                            .customerId(req.getCustomerId())
                            .storeId(req.getStoreId())
                            .items(req.getItems().stream()
                                    .map(i -> new ReserveStockRequest.StockItemRequest(i.getSku(), i.getQuantity()))
                                    .toList())
                            .build();

                    // 2. Reserve stock first (before saving order)
                    return inventoryClient.reserveStock(reserveReq)
                            .collectList()
                            .flatMap(reservations -> {
                                if (reservations.isEmpty()) {
                                    return Mono.error(new InsufficientStockException("Insufficient stock"));
                                }
                                // 3. Save order + items (transactional)
                                return orderRepo.save(order)
                                        .flatMap(savedOrder -> {
                                            items.forEach(item -> item.setOrderId(savedOrder.getId()));
                                            return orderItemRepo.saveAll(items)
                                                    .collectList()
                                                    .map(savedItems -> savedOrder);
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
                                                        .thenReturn(mapToResponse(savedOrder, items));
                                            }
                                            return Mono.just(mapToResponse(savedOrder, items));
                                        })
                                        .onErrorResume(saveError -> {
                                            log.error("Failed to save order after reserve, cancelling reservations", saveError);
                                            return inventoryClient.cancelOrderReservations(orderUuid)
                                                    .then(Mono.error(saveError));
                                        });
                            })
                            .onErrorMap(e -> {
                                log.error("Failed to reserve stock", e);
                                if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                    int status = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getStatusCode().value();
                                    if (status >= 500 || status == 408) {
                                        return new ServiceUnavailableException("Inventory service unavailable", e);
                                    }
                                }
                                String msg = e.getMessage();
                                if (msg != null && (msg.contains("Connection") || msg.contains("timeout") || msg.contains("Timeout"))) {
                                    return new ServiceUnavailableException("Inventory service unavailable", e);
                                }
                                return new InsufficientStockException("Insufficient stock or product not found", e);
                            });
                });
    }


    public Mono<OrderResponse> mockPayment(String orderUuid) {
        return orderRepo.findByOrderUuid(orderUuid)
                .flatMap(order -> {
                    if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
                        return Mono.error(new InvalidOrderStateException("COD orders cannot be paid online"));
                    }
                    if (!"PENDING_PAYMENT".equals(order.getStatus())) {
                        return Mono.error(new InvalidOrderStateException("Order is not in pending payment state"));
                    }

                    order.setStatus("CONFIRMED");
                    order.setPaymentStatus("PAID");

                    return orderRepo.save(order)
                            .flatMap(savedOrder ->
                                    inventoryClient.confirmReservation(savedOrder.getOrderUuid())
                                            .then(orderItemRepo.findByOrderId(savedOrder.getId()).collectList())
                                            .map(items -> {
                                                notificationClient.sendOrderConfirmedEvent(savedOrder)
                                                        .subscribe(v -> {}, e -> log.error(
                                                                "Notification failed for order {}",
                                                                savedOrder.getOrderUuid(), e));
                                                return mapToResponse(savedOrder, items);
                                            })
                            );
                })
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found")));
    }

    private OrderResponse mapToResponse(Order order, List<OrderItem> items) {
        List<OrderResponse.OrderItemResponse> itemResponses = items.stream()
                .map(i -> new OrderResponse.OrderItemResponse(
                        i.getSku(), i.getQty(), i.getUnitPrice(), i.getSubTotal()))
                .toList();

        String message = "CONFIRMED".equals(order.getStatus())
                ? "Order placed successfully"
                : "Please proceed to payment";

        return OrderResponse.builder()
                .orderId(order.getOrderUuid())
                .status(order.getStatus())
                .message(message)
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .build();
    }
}
