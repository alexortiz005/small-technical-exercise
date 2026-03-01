package com.eaortiz.producer.controller;

import com.eaortiz.producer.mqtt.DeviceUpdatePayload;
import com.eaortiz.producer.mqtt.MqttUpdatePublisher;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock controller: simulates a device sending an update via MQTT.
 * POST body is published to the MQTT updates topic; the subscriber then applies it to the repository.
 * Only registered when MQTT is enabled.
 */
@RestController
@RequestMapping("/api/mock")
@ConditionalOnBean(MqttUpdatePublisher.class)
@AllArgsConstructor
public class MockDeviceController {

    private final MqttUpdatePublisher publisher;

    /**
     * Simulates a device sending a temperature update over MQTT.
     * Body: {"deviceId": "uuid", "roomTemperature": 22.5}
     */
    @PostMapping("/device-update")
    public ResponseEntity<Void> sendDeviceUpdate(@RequestBody DeviceUpdatePayload payload) {
        publisher.publishUpdate(payload);
        return ResponseEntity.accepted().build();
    }
}
