package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.domain.OrderItem;
import com.quickcommerce.order.dto.*;
import com.quickcommerce.order.repository.OrderItemRepository;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

    @Transactional
    public Mono<OrderResponse> createOrder(CreateOrderRequest req) {
        log.info("Creating order for customer: {}", req.getCustomerId());
        List<String> skus = req.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getSku)
                .toList();

        // 1. Fetch Prices & Validate
        return catalogClient.getPrices(skus)
                .collectList()
                .flatMap(productPrices -> {
                    Map<String, BigDecimal> priceMap = productPrices.stream()
                            .collect(Collectors.toMap(ProductPriceResponse::getSku, ProductPriceResponse::getPrice));

                    // Validation: Ensure all items exist
                    for (String sku : skus) {
                        if (!priceMap.containsKey(sku)) {
                            return Mono.error(new RuntimeException("Product not found: " + sku));
                        }
                    }

                    // 2. Build Order & Items
                    Order order = Order.builder()
                            .orderUuid(UUID.randomUUID().toString())
                            .storeId(req.getStoreId())
                            .customerId(req.getCustomerId().toString()) // Storing as String for flexibility
                            .paymentMethod(req.getPaymentMethod())
                            .currency("ZMW")
                            .build();

                    boolean isCod = "COD".equalsIgnoreCase(req.getPaymentMethod());
                    if (isCod) {
                        order.setStatus("CONFIRMED");
                        order.setPaymentStatus("COD_PENDING");
                    } else {
                        order.setStatus("PENDING_PAYMENT");
                        order.setPaymentStatus("PENDING");
                    }

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
                    order.setTotalAmount(totalAmount);

                    // 3. Save Order & Items
                    return orderRepo.save(order)
                            .flatMap(savedOrder -> {
                                items.forEach(item -> item.setOrderId(savedOrder.getId()));
                                return orderItemRepo.saveAll(items)
                                        .collectList()
                                        .map(savedItems -> savedOrder);
                            })
                            .flatMap(savedOrder -> {
                                // 4. Reserve/Commit Stock
                                ReserveStockRequest reserveReq = ReserveStockRequest.builder()
                                        .orderId(savedOrder.getOrderUuid()) // Use UUID for external ref
                                        .customerId(req.getCustomerId())
                                        .items(req.getItems().stream()
                                                .map(i -> new ReserveStockRequest.StockItemRequest(i.getSku(), i.getQuantity()))
                                                .toList())
                                        .build();

                                Mono<Boolean> inventoryAction;
                                if (isCod) {
                                    // For COD, we ideally want to commit immediately. 
                                    // Since our Inventory API splits Reserve -> Confirm, we do both.
                                    inventoryAction = inventoryClient.reserveStock(reserveReq)
                                            .collectList()
                                            .flatMap(res -> inventoryClient.confirmReservation(savedOrder.getOrderUuid())) // Confirm using UUID
                                            .thenReturn(true)
                                            .onErrorResume(e -> {
                                                log.error("Failed to commit stock for COD order", e);
                                                return Mono.just(false);
                                            });
                                } else {
                                    inventoryAction = inventoryClient.reserveStock(reserveReq)
                                            .collectList()
                                            .map(res -> !res.isEmpty())
                                            .onErrorResume(e -> {
                                                log.error("Failed to reserve stock", e);
                                                return Mono.just(false);
                                            });
                                }

                                return inventoryAction.flatMap(success -> {
                                    if (!success) {
                                        // Rollback: Cancel order manually since R2DBC rollback might be tricky across services
                                        // or throw exception to trigger TX rollback (but external call already happened)
                                        // Best effort: Fail the order
                                        return Mono.error(new RuntimeException("Insufficient Stock or Inventory Error"));
                                    }

                                    // 5. Notification (Fire & Forget for COD)
                                    if (isCod) {
                                        notificationClient.sendOrderConfirmedEvent(savedOrder).subscribe();
                                    }

                                    return Mono.just(mapToResponse(savedOrder, items));
                                });
                            });
                });
    }

    @Transactional
    public Mono<OrderResponse> mockPayment(String orderUuid) {
        return orderRepo.findByOrderUuid(orderUuid)
                .flatMap(order -> {
                    if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
                        return Mono.error(new RuntimeException("COD orders cannot be paid online"));
                    }
                    if (!"PENDING_PAYMENT".equals(order.getStatus())) {
                        return Mono.error(new RuntimeException("Order is not in pending payment state"));
                    }

                    order.setStatus("CONFIRMED");
                    order.setPaymentStatus("PAID");

                    return orderRepo.save(order)
                            .flatMap(savedOrder ->
                                    inventoryClient.confirmReservation(savedOrder.getOrderUuid())
                                            .then(orderItemRepo.findByOrderId(savedOrder.getId()).collectList())
                                            .map(items -> {
                                                notificationClient.sendOrderConfirmedEvent(savedOrder).subscribe();
                                                return mapToResponse(savedOrder, items);
                                            })
                            );
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found")));
    }
    
    // Helper to map Entity to DTO
    private OrderResponse mapToResponse(Order order, List<OrderItem> items) {
        List<OrderResponse.OrderItemResponse> itemResponses = items.stream()
                .map(i -> new OrderResponse.OrderItemResponse(
                        i.getSku(), i.getQty(), i.getUnitPrice(), i.getSubTotal()))
                .toList();
        
        return OrderResponse.builder()
                .orderId(order.getOrderUuid())
                .status(order.getStatus())
                .message("Order processed successfully")
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .build();
    }
}
