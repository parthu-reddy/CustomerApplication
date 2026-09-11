package com.fooddelivery.order.refund;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class RefundQuoteTest {

    @Test
    void testQuoteProratesTax() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);

        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        // Total items sum = 100 + 50 = 150
        order.setSgst(new BigDecimal("7.50"));
        order.setCgst(new BigDecimal("7.50"));
        order.setTotalAmount(new BigDecimal("165.00")); // Including tax
        order.setItemTotal(new BigDecimal("150.00"));

        OrderItem item1 = new OrderItem();
        item1.setId(UUID.randomUUID());
        item1.setPrice(new BigDecimal("100.00"));
        item1.setQuantity(1);

        OrderItem item2 = new OrderItem();
        item2.setId(UUID.randomUUID());
        item2.setPrice(new BigDecimal("50.00"));
        item2.setQuantity(1);
        
        order.setOrderItems(java.util.Set.of(item1, item2));
        
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(itemRepo.sumCompletedQuantity(eq(item1.getId()))).thenReturn(0);

        RefundCommand.Item itemCmd = new RefundCommand.Item();
        itemCmd.setOrderItemId(item1.getId());
        itemCmd.setQuantity(1);

        BigDecimal result = service.quote(orderId, List.of(itemCmd));

        // The item is 100 of a 150 item total, so it carries two thirds of each tax:
        // 100.00 + 5.00 SGST + 5.00 CGST = 110.00. Dropping the proration under-refunds by 10.00.
        assertEquals(new BigDecimal("110.00"), result);
    }

    /** Dividing by a zero item total would either throw or silently produce nonsense. */
    @Test
    void quoteRefusesWhenThereIsNoItemTotalToProrateAgainst() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);
        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setItemTotal(BigDecimal.ZERO);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.quote(orderId, List.of()));
        assertEquals("QUOTE_UNAVAILABLE", e.getMessage());
    }

    @Test
    void quoteRefusesAnItemThatHasAlreadyBeenRefunded() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);
        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setItemTotal(new BigDecimal("150.00"));
        order.setSgst(new BigDecimal("7.50"));
        order.setCgst(new BigDecimal("7.50"));

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        order.setOrderItems(java.util.Set.of(item));

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        // Both units were refunded on an earlier ticket.
        when(itemRepo.sumCompletedQuantity(eq(item.getId()))).thenReturn(2);

        RefundCommand.Item itemCmd = new RefundCommand.Item();
        itemCmd.setOrderItemId(item.getId());
        itemCmd.setQuantity(1);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.quote(orderId, List.of(itemCmd)));
        assertEquals("ITEM_ALREADY_REFUNDED", e.getMessage());
    }

    @Test
    void quoteAllowsTheUnrefundedRemainderOfAPartlyRefundedItem() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);
        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setItemTotal(new BigDecimal("100.00"));
        order.setSgst(new BigDecimal("2.50"));
        order.setCgst(new BigDecimal("2.50"));

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        order.setOrderItems(java.util.Set.of(item));

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(itemRepo.sumCompletedQuantity(eq(item.getId()))).thenReturn(1);

        RefundCommand.Item itemCmd = new RefundCommand.Item();
        itemCmd.setOrderItemId(item.getId());
        itemCmd.setQuantity(1);

        // 50.00 of a 100.00 item total, so half of each 2.50 tax: 50.00 + 1.25 + 1.25.
        assertEquals(new BigDecimal("52.50"), service.quote(orderId, List.of(itemCmd)));
    }
}
