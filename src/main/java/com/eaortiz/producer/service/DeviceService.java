package com.eaortiz.producer.service;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.eaortiz.producer.mqtt.DeviceUpdatePayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for device operations. Handles create-or-update from external updates (e.g. MQTT).
 * Uses the outbox pattern: device and outbox entry are written in the same transaction so that
 * the full state of all devices is eventually published to Kafka atomically with the DB update.
 */
@Service
@AllArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a device or updates it if one with the given id exists. Identified by id; names may duplicate.
     * In the same transaction, writes an outbox entry with the full state of all devices for Kafka.
     */
    @Transactional
    public void createOrUpdate(DeviceUpdatePayload payload) {
        deviceRepository.findById(payload.deviceId())
                .map(existing -> {
                    existing.setName(payload.name());
                    existing.setRoomTemperature(payload.roomTemperature());
                    return deviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    Device newDevice = Device.builder()
                            .id(payload.deviceId())
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
        outboxService.appendSnapshot(payloadJson);
    }
}
