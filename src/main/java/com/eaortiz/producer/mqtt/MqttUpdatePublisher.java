package com.eaortiz.producer.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Publishes device update payloads to the MQTT updates topic (e.g. for mocking a device).
 */
@Service
@ConditionalOnBean(MqttClient.class)
public class MqttUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttUpdatePublisher.class);

    @Value("${mqtt.topic-updates}")
    private String topicUpdates;

    private final MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttUpdatePublisher(MqttClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a device update to the MQTT topic as if a device had sent it.
     */
    public void publishUpdate(DeviceUpdatePayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            String topic = topicUpdates;
            if (client.isConnected()) {
                client.publish(topic, message);
                log.debug("Published device update to {}: id={}, name={}", topic, payload.deviceId(), payload.name());
            } else {
                log.warn("MQTT client not connected; cannot publish update for device {}", payload.name());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload", e);
        } catch (MqttException e) {
            log.error("Failed to publish device update to MQTT", e);
            throw new IllegalStateException("MQTT publish failed", e);
        }
    }
}
