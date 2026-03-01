package com.eaortiz.producer.kafka;

import com.eaortiz.producer.domain.OutboxEntry;
import com.eaortiz.producer.service.OutboxService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Polls the outbox for the single PENDING entry via {@link OutboxService}, publishes it to Kafka, then marks it PUBLISHED.
 */
@Component
@AllArgsConstructor
public class KafkaUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaUpdatePublisher.class);

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        Optional<OutboxEntry> pending = outboxService.getPending();
        pending.ifPresent(entry -> {
            try {
                kafkaTemplate.send(entry.getTopic(), entry.getPartitionKey(), entry.getPayload()).join();
                outboxService.markPublished(entry);
            } catch (Exception e) {
                log.warn("Failed to publish outbox entry {} to topic {}: {}", entry.getId(), entry.getTopic(), e.getMessage());
            }
        });
    }
}
