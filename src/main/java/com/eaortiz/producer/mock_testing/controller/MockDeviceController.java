package com.eaortiz.producer.mock_testing.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.mock_testing.mqtt.MqttUpdatePublisher;
import com.eaortiz.producer.mock_testing.service.DeviceSnapshotService;
import com.eaortiz.producer.mqtt.DeviceUpdatePayload;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock controller: simulates devices sending updates via MQTT, and exposes the latest devices-state snapshot.
 * POST body is published to the MQTT updates topic; the subscriber then applies it to the repository.
 * GET /devices-state returns the latest snapshot of all devices (from the last Kafka message consumed).
 * Only registered when MQTT is enabled.
 */
@RestController
@RequestMapping("/api/mock")
@ConditionalOnBean(MqttUpdatePublisher.class)
@AllArgsConstructor
@Slf4j
public class MockDeviceController {

    private static final double TEMPERATURE_MIN_CELSIUS = 18.0;
    private static final double TEMPERATURE_MAX_CELSIUS = 28.0;

    private final MqttUpdatePublisher publisher;
    private final DeviceSnapshotService deviceSnapshotService;

    /**
     * Simulates a device sending a temperature update over MQTT.
     */
    @PostMapping("/device-update")
    public ResponseEntity<Void> sendDeviceUpdate(@RequestBody DeviceUpdatePayload payload) {
        log.info("Publishing device update to MQTT: deviceId={}, name={}, status={}, roomTemperature={}°C",
                payload.deviceId(), payload.name(), payload.status(), payload.roomTemperature());
        publisher.publishUpdate(payload);
        log.debug("Device update published for deviceId={}", payload.deviceId());
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
     *                         device, random temperature in [18, 28] °C, and random status (to trigger random validation errors). Must be at least 1.
     * @return 202 Accepted after all requests have been published to the MQTT topic.
     */
    @PostMapping("/device-updates/bulk")
    public ResponseEntity<Void> sendBulkDeviceUpdates(
            @RequestParam(name = "amountOfDevices") int amountOfDevices,
            @RequestParam(name = "amountOfRequests") int amountOfRequests) {
        log.info("Bulk device updates requested: amountOfDevices={}, amountOfRequests={}", amountOfDevices, amountOfRequests);
        if (amountOfDevices < 1 || amountOfRequests < 1) {
            log.warn("Bulk request rejected: amountOfDevices and amountOfRequests must be at least 1");
            return ResponseEntity.badRequest().build();
        }
        Device.Status[] statuses = Device.Status.values();
        List<DeviceUpdatePayload> devices = IntStream.range(0, amountOfDevices)
                .mapToObj(i -> new DeviceUpdatePayload(
                        UUID.randomUUID(),
                        "mock-device-" + (i + 1),
                        ThreadLocalRandom.current().nextDouble(TEMPERATURE_MIN_CELSIUS, TEMPERATURE_MAX_CELSIUS),
                        Device.Status.ACTIVE))
                .toList();
        log.debug("Created {} mock devices for bulk run", devices.size());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int r = 0; r < amountOfRequests; r++) {
            DeviceUpdatePayload device = devices.get(random.nextInt(devices.size()));
            double temperature = random.nextDouble(TEMPERATURE_MIN_CELSIUS, TEMPERATURE_MAX_CELSIUS);
            Device.Status randomStatus = statuses[random.nextInt(statuses.length)];
            publisher.publishUpdate(new DeviceUpdatePayload(device.deviceId(), device.name(), temperature, randomStatus));
        }
        log.info("Bulk complete: published {} device updates to MQTT ({} distinct devices)", amountOfRequests, amountOfDevices);
        return ResponseEntity.accepted().build();
    }

    /**
     * Returns the latest devices-state snapshot (all devices, from the last consumed Kafka message).
     */
    @GetMapping("/devices-state")
    public List<Device> getLatestDevicesStateSnapshot() {
        List<Device> snapshot = deviceSnapshotService.getSnapshot();
        log.info("Devices-state snapshot requested: returning {} device(s)", snapshot.size());
        return snapshot;
    }
}
