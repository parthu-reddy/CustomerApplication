package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.*;
import com.fooddelivery.order.enums.*;
import com.fooddelivery.order.repository.*;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Spring transaction/outbox atomicity, with retained committed local rows; no server or timer. */
@DataJpaTest(showSql=false)
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages={"com.fooddelivery.order.entity","com.fooddelivery.common.outbox.entity"})
@EnableJpaRepositories(basePackages={"com.fooddelivery.order.repository","com.fooddelivery.common.outbox.repository"})
@Import({RefundService.class,RefundRetryPersistenceTest.Config.class})
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:refundRetry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false","eureka.client.enabled=false"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RefundRetryPersistenceTest {
    @TestConfiguration static class Config { @Bean ObjectMapper mapper(){return new ObjectMapper();} }
    @Autowired RefundService service;
    @Autowired IOrderRepository orders;
    @Autowired IPaymentIntentRepository intents;
    @SpyBean RefundRepository refunds;
    @Autowired OutboxEventRepository outbox;
    @MockBean LedgerBookkeeper ledger;

    @Test void selectedRetryCommitsOnceAndFailedCommitRollsBackOutboxAndRefund() throws Exception {
        Order order=new Order(); order.setId(UUID.randomUUID()); order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID()); order.setTotalAmount(new BigDecimal("43.35"));
        order.setStatus(OrderStatus.CANCELLED);order.setDispatchCityId("BLR");order.setFleetSearchRadiusKm(5.0);
        orders.saveAndFlush(order);
        PaymentIntent intent=new PaymentIntent();intent.setId(UUID.randomUUID());intent.setInternalOrderId(order.getId());
        intent.setAmount(new BigDecimal("43.35"));intent.setStatus(PaymentIntentStatus.SUCCESS);
        intent.setPaymentMethod(PaymentMethod.CARD);intent.setGatewayName(PaymentGateway.RAZORPAY);
        intent.setGatewayOrderId("mock_rzp_txn_"+order.getId());intents.saveAndFlush(intent);
        Refund selected=saveRefund(order,intent,"13.35");Refund unrelated=saveRefund(order,intent,"10.00");
        UUID selectedId=selected.getId(),unrelatedId=unrelated.getId();
        // Force failure after the real outbox insert. The service's Spring transaction must roll
        // back both changes even though the caller is outside a test transaction.
        doThrow(new IllegalStateException("refund write unavailable")).when(refunds).save(any(Refund.class));
        assertThrows(IllegalStateException.class,()->service.retryFailed(selectedId));
        reset(refunds);
        assertEquals(0,outbox.count());assertEquals(RefundStatus.FAILED,refunds.findById(selectedId).orElseThrow().getStatus());
        assertEquals(4,refunds.findById(selectedId).orElseThrow().getAttempts());
        service.retryFailed(selectedId);
        assertEquals(1,outbox.count());Refund loaded=refunds.findById(selectedId).orElseThrow();
        assertEquals(RefundStatus.PROCESSING,loaded.getStatus());assertEquals(5,loaded.getAttempts());
        assertEquals(RefundStatus.FAILED,refunds.findById(unrelatedId).orElseThrow().getStatus());
        assertEquals(4,refunds.findById(unrelatedId).orElseThrow().getAttempts());
        var event=outbox.findAll().get(0);assertEquals("refund_retry:"+selectedId+":5",event.getIdempotencyKey());
        assertEquals(selectedId.toString(),new ObjectMapper().readTree(event.getPayload()).path("refundId").asText());
        assertThrows(IllegalStateException.class,()->service.retryFailed(selectedId));assertEquals(1,outbox.count());
        verifyNoInteractions(ledger); // Queueing is not completion or money posting.
    }

    Refund saveRefund(Order order,PaymentIntent intent,String amount){
        return refunds.saveAndFlush(Refund.builder().id(UUID.randomUUID()).orderId(order.getId())
                .paymentIntentId(intent.getId()).amount(new BigDecimal(amount)).currency("INR")
                .reasonCode("CANCELLED").reasonText("Previous callback failed").faultType(com.fooddelivery.order.enums.FaultType.UNKNOWN)
                .destination(RefundDestination.ORIGINAL_METHOD).source(RefundSource.SYSTEM_CANCELLATION)
                .initiatedByType(InitiatorType.SYSTEM).status(RefundStatus.FAILED).attempts(4)
                .failureReason("Previous callback failed").idempotencyKey(UUID.randomUUID().toString()).build());
    }
}
