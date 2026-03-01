package com.eaortiz.producer.controller;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.service.DeviceSnapshotService;
import com.eaortiz.producer.mqtt.DeviceUpdatePayload;
import com.eaortiz.producer.mqtt.MqttUpdatePublisher;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mock controller: simulates a device sending an update via MQTT, and exposes the latest device-state snapshot.
 * POST body is published to the MQTT updates topic; the subscriber then applies it to the repository.
 * GET returns the latest snapshot of all devices (from the last Kafka message consumed).
 * Only registered when MQTT is enabled.
 */
@RestController
@RequestMapping("/api/mock")
@ConditionalOnBean(MqttUpdatePublisher.class)
@AllArgsConstructor
public class MockDeviceController {

    private final MqttUpdatePublisher publisher;
    private final DeviceSnapshotService deviceSnapshotService;

    /**
     * Simulates a device sending a temperature update over MQTT.
     * Body: {"deviceId": "uuid", "name": "living-room", "roomTemperature": 22.5}
     */
    @PostMapping("/device-update")
    public ResponseEntity<Void> sendDeviceUpdate(@RequestBody DeviceUpdatePayload payload) {
        publisher.publishUpdate(payload);
        return ResponseEntity.accepted().build();
    }

    /**
     * Returns the latest snapshot of all devices (current state from the last consumed Kafka message).
     */
    @GetMapping("/device-state")
    public List<Device> getLatestDeviceStateSnapshot() {
        return deviceSnapshotService.getSnapshot();
    }
}
