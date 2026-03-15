package com.quickcommerce.order.service;

import com.quickcommerce.order.client.CatalogClient;
import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.domain.OrderStatus;
import com.quickcommerce.order.domain.PaymentStatus;
import com.quickcommerce.order.dto.PagedOrderResponse;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderItemRepository;
import com.quickcommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderService pagination logic.
 * Uses Mockito mocks — no Docker / DB required.
 */
@ExtendWith(MockitoExtension.class)
class OrderServicePaginationTest {

    @Mock private OrderRepository orderRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private OrderEventRepository orderEventRepo;
    @Mock private CatalogClient catalogClient;
    @Mock private InventoryClient inventoryClient;
    @Mock private NotificationClient notificationClient;
    @Mock private TransactionalOperator transactionalOperator;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepo, orderItemRepo, orderEventRepo,
                catalogClient, inventoryClient, notificationClient,
                transactionalOperator
        );
        ReflectionTestUtils.setField(orderService, "deliveryFee", new BigDecimal("15.00"));
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Order order(Long id, String customerId, Long storeId) {
        return Order.builder()
                .id(id)
                .orderUuid("UUID-" + id)
                .customerId(customerId)
                .storeId(storeId)
                .status(OrderStatus.CONFIRMED.name())
                .paymentMethod("COD")
                .paymentStatus(PaymentStatus.COD_PENDING.name())
                .totalAmount(new BigDecimal("50.00"))
                .deliveryFee(new BigDecimal("15.00"))
                .currency("ZMW")
                .deliveryAddress("Test Address")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private List<Order> orders(int count, String customerId, Long storeId) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> order((long) i, customerId, storeId))
                .toList();
    }

    // ─── getCustomerOrders ───────────────────────────────────────────────────

    @Test
    @DisplayName("Customer orders: first page returns correct items, first=true, last=false")
    void customerOrders_firstPage() {
        String customerId = "CUST_001";
        List<Order> page = orders(5, customerId, 1L);

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(0, 5))))
                .thenReturn(Flux.fromIterable(page));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(12L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 0, 5))
                .assertNext(r -> {
                    assertThat(r.getContent()).hasSize(5);
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.getPage()).isEqualTo(0);
                    assertThat(m.getSize()).isEqualTo(5);
                    assertThat(m.getTotalElements()).isEqualTo(12);
                    assertThat(m.getTotalPages()).isEqualTo(3);
                    assertThat(m.isFirst()).isTrue();
                    assertThat(m.isLast()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Customer orders: second page returns correct items, first=false, last=false")
    void customerOrders_middlePage() {
        String customerId = "CUST_001";
        List<Order> page = orders(5, customerId, 1L);

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(1, 5))))
                .thenReturn(Flux.fromIterable(page));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(12L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 1, 5))
                .assertNext(r -> {
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.isFirst()).isFalse();
                    assertThat(m.isLast()).isFalse();
                    assertThat(m.getPage()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Customer orders: last page with partial results, last=true")
    void customerOrders_lastPagePartial() {
        String customerId = "CUST_001";
        List<Order> partial = orders(2, customerId, 1L);

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(2, 5))))
                .thenReturn(Flux.fromIterable(partial));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(12L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 2, 5))
                .assertNext(r -> {
                    assertThat(r.getContent()).hasSize(2);
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.isLast()).isTrue();
                    assertThat(m.isFirst()).isFalse();
                    assertThat(m.getTotalPages()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Customer orders: customer with no orders returns empty, totalElements=0, first=true, last=true")
    void customerOrders_empty() {
        String customerId = "CUST_NEW";

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(0, 20))))
                .thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(0L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 0, 20))
                .assertNext(r -> {
                    assertThat(r.getContent()).isEmpty();
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.getTotalElements()).isEqualTo(0);
                    assertThat(m.getTotalPages()).isEqualTo(0);
                    assertThat(m.isFirst()).isTrue();
                    assertThat(m.isLast()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Customer orders: out-of-range page returns empty content, last=true")
    void customerOrders_outOfRange() {
        String customerId = "CUST_001";

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(99, 5))))
                .thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(10L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 99, 5))
                .assertNext(r -> {
                    assertThat(r.getContent()).isEmpty();
                    assertThat(r.getMeta().isLast()).isTrue();
                    assertThat(r.getMeta().getPage()).isEqualTo(99);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Customer orders: exactly pageSize orders is single page, first=true, last=true")
    void customerOrders_exactlyPageSize() {
        String customerId = "CUST_001";
        List<Order> page = orders(5, customerId, 1L);

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(0, 5))))
                .thenReturn(Flux.fromIterable(page));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(5L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 0, 5))
                .assertNext(r -> {
                    assertThat(r.getContent()).hasSize(5);
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.getTotalPages()).isEqualTo(1);
                    assertThat(m.isFirst()).isTrue();
                    assertThat(m.isLast()).isTrue();
                })
                .verifyComplete();
    }

    // ─── getStoreOrders ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Store orders: without status filter, first page, correct metadata")
    void storeOrders_noFilter_firstPage() {
        Long storeId = 1L;
        List<Order> page = orders(5, "CUST_X", storeId);

        when(orderRepo.findByStoreIdOrderByCreatedAtDesc(eq(storeId), eq(PageRequest.of(0, 5))))
                .thenReturn(Flux.fromIterable(page));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByStoreId(storeId)).thenReturn(Mono.just(8L));

        StepVerifier.create(orderService.getStoreOrders(storeId, null, 0, 5))
                .assertNext(r -> {
                    assertThat(r.getContent()).hasSize(5);
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.getTotalElements()).isEqualTo(8);
                    assertThat(m.getTotalPages()).isEqualTo(2);
                    assertThat(m.isFirst()).isTrue();
                    assertThat(m.isLast()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Store orders: with status filter uses correct query and count")
    void storeOrders_withStatusFilter() {
        Long storeId = 1L;
        List<Order> page = orders(3, "CUST_X", storeId);

        when(orderRepo.findByStoreIdAndStatusOrderByCreatedAtDesc(eq(storeId), eq("CONFIRMED"), eq(PageRequest.of(0, 20))))
                .thenReturn(Flux.fromIterable(page));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByStoreIdAndStatus(storeId, "CONFIRMED")).thenReturn(Mono.just(3L));

        StepVerifier.create(orderService.getStoreOrders(storeId, "confirmed", 0, 20))
                .assertNext(r -> {
                    assertThat(r.getContent()).hasSize(3);
                    PagedOrderResponse.PageMeta m = r.getMeta();
                    assertThat(m.getTotalElements()).isEqualTo(3);
                    assertThat(m.getTotalPages()).isEqualTo(1);
                    assertThat(m.isFirst()).isTrue();
                    assertThat(m.isLast()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Store orders: blank status treated as no filter")
    void storeOrders_blankStatusTreatedAsNoFilter() {
        Long storeId = 1L;
        List<Order> page = orders(2, "CUST_X", storeId);

        when(orderRepo.findByStoreIdOrderByCreatedAtDesc(eq(storeId), eq(PageRequest.of(0, 10))))
                .thenReturn(Flux.fromIterable(page));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByStoreId(storeId)).thenReturn(Mono.just(2L));

        StepVerifier.create(orderService.getStoreOrders(storeId, "   ", 0, 10))
                .assertNext(r -> assertThat(r.getContent()).hasSize(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("meta.size always reflects requested page size, not actual items returned")
    void metaSize_reflectsRequestedSize() {
        String customerId = "CUST_001";
        List<Order> partial = orders(3, customerId, 1L);

        when(orderRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), eq(PageRequest.of(1, 10))))
                .thenReturn(Flux.fromIterable(partial));
        when(orderItemRepo.findByOrderId(any())).thenReturn(Flux.empty());
        when(orderRepo.countByCustomerId(customerId)).thenReturn(Mono.just(13L));

        StepVerifier.create(orderService.getCustomerOrders(customerId, 1, 10))
                .assertNext(r -> {
                    assertThat(r.getContent()).hasSize(3);
                    assertThat(r.getMeta().getSize()).isEqualTo(10);
                })
                .verifyComplete();
    }
}
