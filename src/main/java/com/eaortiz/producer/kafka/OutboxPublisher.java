package com.eaortiz.producer.kafka;

import com.eaortiz.producer.domain.OutboxEntry;
import com.eaortiz.producer.domain.OutboxRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox table for PENDING entries and publishes them to Kafka, then marks them PUBLISHED.
 * Ensures device-state updates are eventually published without requiring Kafka to be in the same
 * transaction as the database.
 */
@Component
@AllArgsConstructor
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEntry> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEntry.Status.PENDING);
        for (OutboxEntry entry : pending) {
            try {
                kafkaTemplate.send(entry.getTopic(), entry.getPartitionKey(), entry.getPayload()).join();
                entry.setStatus(OutboxEntry.Status.PUBLISHED);
                outboxRepository.save(entry);
            } catch (Exception e) {
                log.warn("Failed to publish outbox entry {} to topic {}: {}", entry.getId(), entry.getTopic(), e.getMessage());
            }
        }
    }
}
