package com.fooddelivery.order.service;

import com.fooddelivery.common.messaging.DeadLetterReplayRequest;
import com.fooddelivery.common.messaging.DeadLetterReplayResult;
import com.fooddelivery.common.messaging.DeadLetterReplayer;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.repository.RefundRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminDlqService {

    private final DeadLetterReplayer deadLetterReplayer;
    private final RefundRepository refundRepository;
    private final RefundService refundService;

    public AdminDlqService(ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate,
                           RefundRepository refundRepository, RefundService refundService) {
        this.deadLetterReplayer = new DeadLetterReplayer(consumerFactory, kafkaTemplate);
        this.refundRepository = refundRepository;
        this.refundService = refundService;
    }

    /**
     * Replays one dead-letter record onto the topic it failed on, with its original key, value and
     * headers. This service's listeners dead-letter two ways -- {@code <topic>-dlt} through
     * {@code @RetryableTopic}, {@code <topic>.DLT} through the shared DefaultErrorHandler -- and both
     * are replayed the same way.
     */
    public DeadLetterReplayResult replayDeadLetter(DeadLetterReplayRequest request) {
        return deadLetterReplayer.replay(request);
    }

    public void retryRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found for ID: " + refundId));
        
        if (refund.getStatus() != com.fooddelivery.common.enums.RefundStatus.FAILED) {
            throw new IllegalArgumentException("Refund can only be retried if status is FAILED. Current status: " + refund.getStatus());
        }
        
        log.info("Admin manually retrying refund for ID: {}", refundId);
        
        refund.setStatus(com.fooddelivery.common.enums.RefundStatus.PROCESSING);
        refund.setFailureReason(null);
        refundRepository.save(refund);
        
        refundService.retryStuck();
    }
}
