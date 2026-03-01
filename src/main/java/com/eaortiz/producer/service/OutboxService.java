package com.eaortiz.producer.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eaortiz.producer.domain.OutboxEntry;
import com.eaortiz.producer.domain.OutboxRepository;

/**
 * Application service for the outbox. Handles the device-state snapshot outbox only (topic and partition key
 * are fixed). Use this to append snapshots and to poll/mark entries; no direct use of {@link OutboxRepository}.
 */
@Service
public class OutboxService {

    private static final String SNAPSHOT_PARTITION_KEY = "snapshot";

    private final OutboxRepository outboxRepository;
    private final String deviceStateTopic;

    public OutboxService(OutboxRepository outboxRepository,
                         @Value("${kafka.topic.device-state}") String deviceStateTopic) {
        this.outboxRepository = outboxRepository;
        this.deviceStateTopic = deviceStateTopic;
    }

    /**
     * Appends a PENDING outbox entry for the device-state snapshot. We only care about the latest snapshot
     * (Kafka topic is compacted); any older PENDING entries are irrelevant. So we delete all existing
     * PENDING entries and then save the new one in a single transaction — at most one PENDING entry exists
     * at any time. Atomic so we never lose the new entry without deleting the old ones, or leave stale
     * PENDING entries on rollback.
     */
    @Transactional
    public void appendSnapshot(String payload) {
        outboxRepository.deleteAll(outboxRepository.findByStatus(OutboxEntry.Status.PENDING));
        outboxRepository.save(OutboxEntry.builder()
                .topic(deviceStateTopic)
                .partitionKey(SNAPSHOT_PARTITION_KEY)
                .payload(payload)
                .status(OutboxEntry.Status.PENDING)
                .build());
    }

    /**
     * Returns the latest PENDING entry, if any. Ensures at most one PENDING: keeps the newest
     * (by createdAt) and deletes all others so consistency is always restored.
     */
    @Transactional
    public Optional<OutboxEntry> getPending() {
        List<OutboxEntry> pending = outboxRepository.findByStatus(OutboxEntry.Status.PENDING);
        if (pending.isEmpty()){
            return Optional.empty();
        } 
        pending.sort(Comparator.comparing(OutboxEntry::getCreatedAt));
        OutboxEntry latest = pending.removeLast();
        outboxRepository.deleteAll(pending);
        return Optional.of(latest);
    }
    /**
     * Marks the entry as PUBLISHED. Call after successfully publishing to Kafka.
     */
    @Transactional
    public void markPublished(OutboxEntry entry) {
        entry.setStatus(OutboxEntry.Status.PUBLISHED);
        outboxRepository.save(entry);
    }
}
