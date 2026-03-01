package com.eaortiz.producer.service;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.eaortiz.producer.domain.OutboxEntry;
import com.eaortiz.producer.domain.OutboxRepository;
import com.eaortiz.producer.mqtt.DeviceUpdatePayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for device operations. Handles create-or-update from external updates (e.g. MQTT).
 * Uses the outbox pattern: device and outbox entry are written in the same transaction so that
 * the full state of all devices is eventually published to Kafka atomically with the DB update.
 */
@Service
public class DeviceService {

    private static final String SNAPSHOT_PARTITION_KEY = "snapshot";

    private final DeviceRepository deviceRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String deviceStateTopic;

    public DeviceService(
            DeviceRepository deviceRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${kafka.topic.device-state}") String deviceStateTopic) {
        this.deviceRepository = deviceRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.deviceStateTopic = deviceStateTopic;
    }

    /**
     * Creates a device or updates its room temperature if a device with the same logical key (name) exists.
     * In the same transaction, writes an outbox entry with the full state of all devices for Kafka.
     */
    @Transactional
    public void createOrUpdate(DeviceUpdatePayload payload) {
        String name = payload.name();
        deviceRepository.findByName(name)
                .map(existing -> {
                    existing.setName(payload.name());
                    existing.setRoomTemperature(payload.roomTemperature());
                    return deviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    Device newDevice = Device.builder()
                            .name(payload.name())
                            .roomTemperature(payload.roomTemperature())
                            .build();
                    return deviceRepository.save(newDevice);
                });

        List<Device> fullState = deviceRepository.findAll();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(fullState);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize device state snapshot", e);
        }
        outboxRepository.save(OutboxEntry.builder()
                .topic(deviceStateTopic)
                .partitionKey(SNAPSHOT_PARTITION_KEY)
                .payload(payloadJson)
                .status(OutboxEntry.Status.PENDING)
                .build());
    }
}
