package com.eaortiz.producer.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.eaortiz.producer.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/**
 * Listens to MQTT device update messages and applies them via {@link DeviceService}.
 */
@Component
@AllArgsConstructor
public class MqttUpdateListener implements IMqttMessageListener {

    private static final Logger log = LoggerFactory.getLogger(MqttUpdateListener.class);

    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        try {
            DeviceUpdatePayload update = objectMapper.readValue(payload, DeviceUpdatePayload.class);
            deviceService.createOrUpdate(update);
            log.info("Processed MQTT update for device {} with name {}", update.deviceId(), update.name());
        } catch (Exception e) {
            log.error("Failed to process MQTT update on {}: {}", topic, payload, e);
        }
    }
}
