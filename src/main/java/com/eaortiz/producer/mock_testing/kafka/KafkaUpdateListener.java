package com.eaortiz.producer.mock_testing.kafka;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.mock_testing.service.DeviceSnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock consumer: consumes full device-state snapshots from Kafka and replaces the in-memory store.
 */
@Component
@AllArgsConstructor
public class KafkaUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaUpdateListener.class);
    private static final TypeReference<List<Device>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final DeviceSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topic.device-state}",
            groupId = "mock-consumer"
    )
    public void consume(String payload) {
        try {
            List<Device> snapshot = objectMapper.readValue(payload, SNAPSHOT_TYPE);
            snapshotService.replaceAll(snapshot);
            log.debug("Consumed device-state snapshot: {} devices", snapshot.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse device-state snapshot: {}", e.getMessage());
        }
    }
}
