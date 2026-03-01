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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Mock controller: simulates devices sending updates via MQTT, and exposes the latest device-state snapshot.
 * POST body is published to the MQTT updates topic; the subscriber then applies it to the repository.
 * GET returns the latest snapshot of all devices (from the last Kafka message consumed).
 * Only registered when MQTT is enabled.
 */
@RestController
@RequestMapping("/api/mock")
@ConditionalOnBean(MqttUpdatePublisher.class)
@AllArgsConstructor
public class MockDeviceController {

    private static final double TEMPERATURE_MIN_CELSIUS = 18.0;
    private static final double TEMPERATURE_MAX_CELSIUS = 28.0;

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
     * Generates a fixed set of mock devices and sends a given number of device-update requests,
     * each request using one of those devices (chosen at random) with a random room temperature.
     * Use this to load or stress the pipeline (MQTT → device state → outbox → Kafka → snapshot).
     *
     * @param amountOfDevices  Number of distinct mock devices to create. Each gets a random UUID and
     *                         a name like "mock-device-1". Must be at least 1.
     * @param amountOfRequests Number of device-update requests to send. Each request picks a random
     *                         device from the pool and a random temperature in [18, 28] °C. Must be at least 1.
     * @return 202 Accepted after all requests have been published to the MQTT topic.
     */
    @PostMapping("/device-updates/bulk")
    public ResponseEntity<Void> sendBulkDeviceUpdates(
            @RequestParam(name = "amountOfDevices") int amountOfDevices,
            @RequestParam(name = "amountOfRequests") int amountOfRequests) {
        if (amountOfDevices < 1 || amountOfRequests < 1) {
            return ResponseEntity.badRequest().build();
        }
        List<DeviceUpdatePayload> devices = IntStream.range(0, amountOfDevices)
                .mapToObj(i -> new DeviceUpdatePayload(
                        UUID.randomUUID(),
                        "mock-device-" + (i + 1),
                        ThreadLocalRandom.current().nextDouble(TEMPERATURE_MIN_CELSIUS, TEMPERATURE_MAX_CELSIUS)))
                .toList();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int r = 0; r < amountOfRequests; r++) {
            DeviceUpdatePayload device = devices.get(random.nextInt(devices.size()));
            double temperature = random.nextDouble(TEMPERATURE_MIN_CELSIUS, TEMPERATURE_MAX_CELSIUS);
            publisher.publishUpdate(new DeviceUpdatePayload(device.deviceId(), device.name(), temperature));
        }
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
