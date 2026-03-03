package com.eaortiz.producer.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/**
 * Listens to MQTT device update messages and applies them via {@link DeviceService}.
 * Deserializes to {@link DeviceUpdatePayload} then converts to {@link Device} for the service.
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
            Device device = Device.builder()
                    .id(update.deviceId())
                    .name(update.name())
                    .roomTemperature(update.roomTemperature())
                    .status(update.status())
                    .build();
            deviceService.createOrUpdate(device);
            log.info("Processed MQTT update for device {} with name {}", device.getId(), device.getName());
        } catch (Exception e) {
            log.error("Failed to process MQTT update on {}: {}", topic, payload, e);
        }
    }
}
