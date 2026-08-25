package com.fooddelivery.order.contract;

import com.fooddelivery.common.contract.KafkaStubMessageSender;

import com.fooddelivery.order.service.PaymentEventConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Consumes PaymentGatewayIntegration's real payment_events stub.
 *
 * The assertion is that the consumer gets past its own payload guard
 * (`rootNode.has("orderId") && rootNode.has("gatewayOrderId")`) and looks the payment intent up.
 * That is precisely the CDC question: does this consumer accept the shape the producer actually
 * emits? It would fail if payment-events were ever wrapped in an {eventType, payload} envelope,
 * which is the mistake four other consumers in this codebase make.
 */
@SpringBootTest(classes = PaymentEventConsumerContractTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@ActiveProfiles("contract-test")
@AutoConfigureStubRunner(ids = "com.fooddelivery:payment-service:+:stubs",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@EmbeddedKafka(partitions = 1, topics = {"payment-events"})
class PaymentEventConsumerContractTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    
    @Import(PaymentEventConsumer.class)
    static class TestConfig {
        @Bean
        public MessageVerifierSender<Message<?>> kafkaStubMessageSender(KafkaTemplate<String, String> t) {
            return new KafkaStubMessageSender(t);
        }

        @Bean
        public TransactionTemplate transactionTemplate() {
            PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
            Mockito.when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return new TransactionTemplate(tm);
        }
    }

    @MockBean private com.fooddelivery.common.repository.IIdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean private com.fooddelivery.order.repository.IPaymentIntentRepository paymentIntentRepository;
    @MockBean private com.fooddelivery.order.repository.IOrderRepository orderRepository;
    @MockBean private com.fooddelivery.order.service.state.OrderActionService orderActionService;
    @MockBean private com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    @MockBean private com.fooddelivery.order.service.OrderRefundService orderRefundService;

    @Autowired
    private StubTrigger stubTrigger;

    @Test
    void acceptsTheProducerPayloadAndLooksUpThePaymentIntent() {
        stubTrigger.trigger("payment_events");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(paymentIntentRepository).findByGatewayOrderId(anyString()));
    }
}
