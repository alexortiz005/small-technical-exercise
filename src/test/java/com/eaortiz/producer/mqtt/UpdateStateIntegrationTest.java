package com.eaortiz.producer.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import java.util.Optional;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.eaortiz.producer.outbox.OutboxEntry;
import com.eaortiz.producer.outbox.OutboxRepository;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;

/**
 * Integration test for the MQTT inbound path using an embedded Moquette broker.
 * The app connects and subscribes to the broker; we publish a message and assert
 * that the device is persisted and an outbox entry is written.
 */
@SpringBootTest(properties = {
        "kafka.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class UpdateStateIntegrationTest {

    private static final String MQTT_TOPIC_UPDATES = "devices/updates";

    private static final int EMBEDDED_MQTT_PORT = 18833;

    private static Server embeddedMqttBroker;

    private MqttClient mqttClient;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("mqtt.enabled", () -> "true");
        registry.add("mqtt.broker-url", () -> "tcp://127.0.0.1:" + EMBEDDED_MQTT_PORT);
        registry.add("mqtt.topic-updates", () -> MQTT_TOPIC_UPDATES);
        registry.add("mqtt.client-id", () -> "mqtt-integration-test-subscriber");
    }

    @BeforeAll
    static void startBroker() throws Exception {
        Properties props = new Properties();
        props.setProperty("port", String.valueOf(EMBEDDED_MQTT_PORT));
        props.setProperty("host", "0.0.0.0");
        props.setProperty("allow_anonymous", "true");
        MemoryConfig config = new MemoryConfig(props);
        embeddedMqttBroker = new Server();
        embeddedMqttBroker.startServer(config);
    }

    @AfterAll
    static void stopBroker() {
        if (embeddedMqttBroker != null) {
            embeddedMqttBroker.stopServer();
        }
    }

    @BeforeEach
    void connectClient() throws Exception {
        mqttClient = new MqttClient("tcp://127.0.0.1:" + EMBEDDED_MQTT_PORT, "mqtt-test-publisher");
        mqttClient.connect();
    }

    @AfterEach
    void disconnectClient() throws Exception {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            mqttClient.close();
        }
    }

    /** Poll until the device appears in the repository or timeout. */
    private Optional<Device> waitForDevice(UUID deviceId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<Device> found = deviceRepository.findById(deviceId);
            if (found.isPresent()) {
                return found;
            }
            Thread.sleep(50);
        }
        return deviceRepository.findById(deviceId);
    }

    @Test
    @DisplayName("Given a valid new-device MQTT message, when published to the embedded broker, then device is saved and outbox entry is created")
    void whenMqttMessageArrivesWithValidNewDevice_thenDeviceIsSavedAndOutboxEntryCreated() throws Exception {
        // Given: a valid MQTT payload for a new device (status PENDING)
        UUID deviceId = UUID.randomUUID();
        String payload = """
                {"deviceId":"%s","name":"living-room","roomTemperature":22.5,"status":"PENDING"}
                """.formatted(deviceId).trim();

        // When
        // the message is published to the embedded broker (app is already subscribed)
        mqttClient.publish(MQTT_TOPIC_UPDATES, new MqttMessage(payload.getBytes()));

        // Then
        // the device is persisted with the correct data (poll for async processing)
        Device saved = waitForDevice(deviceId, Duration.ofSeconds(5)).orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo("living-room");
        assertThat(saved.getRoomTemperature()).isEqualTo(22.5);
        assertThat(saved.getStatus()).isEqualTo(Device.Status.PENDING);

        // a PENDING outbox entry is written for the snapshot
        var pending = outboxRepository.findByStatus(OutboxEntry.Status.PENDING);
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().getPayload()).contains(deviceId.toString());
    }

    @Test
    @DisplayName("Given an existing PENDING device, when a valid transition to ACTIVE is published, then device is updated and outbox is appended")
    void whenValidTransitionPendingToActive_thenDeviceUpdatedAndOutboxAppended() throws Exception {
        // Given
        // device exists as PENDING
        UUID deviceId = UUID.randomUUID();
        String pendingPayload = """
                {"deviceId":"%s","name":"kitchen","roomTemperature":21.0,"status":"PENDING"}
                """.formatted(deviceId).trim();
        mqttClient.publish(MQTT_TOPIC_UPDATES, new MqttMessage(pendingPayload.getBytes()));
        Device pendingDevice = waitForDevice(deviceId, Duration.ofSeconds(5))
                .orElseThrow(() -> new AssertionError("Device " + deviceId + " not found after waiting"));
        assertThat(pendingDevice.getStatus()).isEqualTo(Device.Status.PENDING);

        // When
        // valid transition to ACTIVE is published
        String activePayload = """
                {"deviceId":"%s","name":"kitchen","roomTemperature":21.5,"status":"ACTIVE"}
                """.formatted(deviceId).trim();
        mqttClient.publish(MQTT_TOPIC_UPDATES, new MqttMessage(activePayload.getBytes()));
        Device saved = waitForDevice(deviceId, Duration.ofSeconds(5)).orElse(null);
        // poll until status is ACTIVE (second message processed) or timeout
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (saved != null && saved.getStatus() != Device.Status.ACTIVE && System.nanoTime() < deadline) {
            Thread.sleep(50);
            saved = deviceRepository.findById(deviceId).orElse(saved);
        }

        // Then
        // device is ACTIVE with updated temperature
        if (saved == null) {
            throw new AssertionError("Device " + deviceId + " not found or did not transition to ACTIVE in time");
        }
        assertThat(saved.getStatus()).isEqualTo(Device.Status.ACTIVE);
        assertThat(saved.getRoomTemperature()).isEqualTo(21.5);
        assertThat(saved.getName()).isEqualTo("kitchen");
        // outbox has at least one entry containing this device (snapshot appended on update)
        var pendingEntries = outboxRepository.findByStatus(OutboxEntry.Status.PENDING);
        assertThat(pendingEntries).isNotEmpty();
        assertThat(pendingEntries.getLast().getPayload()).contains(deviceId.toString()).contains("ACTIVE");
    }

    @Test
    @DisplayName("Given an existing PENDING device, when an invalid transition to FAULTY is published, then device stays PENDING")
    void whenInvalidTransitionPendingToFaulty_thenDeviceUnchanged() throws Exception {
        // Given: device exists as PENDING
        UUID deviceId = UUID.randomUUID();
        String pendingPayload = """
                {"deviceId":"%s","name":"garage","roomTemperature":18.0,"status":"PENDING"}
                """.formatted(deviceId).trim();
        mqttClient.publish(MQTT_TOPIC_UPDATES, new MqttMessage(pendingPayload.getBytes()));
        Device pendingDevice = waitForDevice(deviceId, Duration.ofSeconds(5))
                .orElseThrow(() -> new AssertionError("Device " + deviceId + " not found after waiting"));
        assertThat(pendingDevice.getStatus()).isEqualTo(Device.Status.PENDING);

        // When: invalid transition PENDING -> FAULTY is published (only PENDING -> ACTIVE is allowed)
        String faultyPayload = """
                {"deviceId":"%s","name":"garage","roomTemperature":36.0,"status":"FAULTY"}
                """.formatted(deviceId).trim();
        mqttClient.publish(MQTT_TOPIC_UPDATES, new MqttMessage(faultyPayload.getBytes()));
        Thread.sleep(500);

        // Then: device is still PENDING (transition rejected)
        Device saved = deviceRepository.findById(deviceId).orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(Device.Status.PENDING); // status should not have changed
        assertThat(saved.getRoomTemperature()).isEqualTo(18.0); // temperature should not have changed
    }
}
