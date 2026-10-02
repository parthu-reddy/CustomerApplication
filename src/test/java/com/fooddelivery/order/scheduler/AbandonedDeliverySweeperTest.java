package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AbandonedDeliverySweeperTest {
    @ParameterizedTest
    @ValueSource(strings = {"abandoned", "completed-after-query", "recent-update", "missing", "already-failed"})
    void lockedRecheckPreventsStaleCandidateFromFailingACompletedOrActiveDelivery(String condition) {
        var repository = mock(IOrderRepository.class);
        var actions = mock(OrderActionService.class);
        var transaction = mock(TransactionTemplate.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        Order candidate = new Order(); candidate.setId(UUID.randomUUID());
        candidate.setStatus(OrderStatus.HANDED_OVER); candidate.setUpdatedAt(Instant.now().minus(Duration.ofHours(3)));
        when(repository.findAbandonedDeliveries(anyList(), any(Instant.class), any())).thenReturn(new PageImpl<>(List.of(candidate)));
        Order current = new Order(); current.setId(candidate.getId()); current.setStatus(OrderStatus.HANDED_OVER);
        current.setDeliveryStatus(DeliveryStatus.OUT_FOR_DELIVERY); current.setUpdatedAt(candidate.getUpdatedAt());
        if (condition.equals("completed-after-query")) { current.setDeliveryStatus(DeliveryStatus.DELIVERED); current.setDeliveredAt(Instant.now()); }
        if (condition.equals("recent-update")) current.setUpdatedAt(Instant.now());
        if (condition.equals("already-failed")) current.setDeliveryStatus(DeliveryStatus.FAILED);
        when(repository.findLockedById(candidate.getId())).thenReturn(condition.equals("missing") ? Optional.empty() : Optional.of(current));
        when(transaction.execute(any())).thenAnswer(i -> ((org.springframework.transaction.support.TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        new AbandonedDeliverySweeper(repository, actions, transaction, redis).sweepAbandonedDeliveries();
        if (condition.equals("abandoned")) {
            verify(repository).save(current);
            verify(actions).emitOrderDeliveryFailedEvent(eq(candidate.getId()), anyString());
        } else {
            verify(repository, never()).save(any()); verifyNoInteractions(actions);
        }
    }
}
