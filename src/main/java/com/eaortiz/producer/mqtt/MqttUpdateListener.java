package com.eaortiz.producer.mqtt;

import com.eaortiz.producer.domain.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listens to MQTT device update messages and applies them to the repository.
 */
@Component
public class MqttUpdateListener implements IMqttMessageListener {

    private static final Logger log = LoggerFactory.getLogger(MqttUpdateListener.class);

    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public MqttUpdateListener(DeviceRepository deviceRepository, ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        try {
            DeviceUpdatePayload update = objectMapper.readValue(payload, DeviceUpdatePayload.class);
            deviceRepository.findById(update.deviceId())
                    .ifPresentOrElse(
                            device -> {
                                device.setRoomTemperature(update.roomTemperature());
                                deviceRepository.save(device);
                                log.info("Updated device {} roomTemperature to {}", device.getId(), update.roomTemperature());
                            },
                            () -> log.warn("Device not found for update: {}", update.deviceId())
                    );
        } catch (Exception e) {
            log.error("Failed to process MQTT update on {}: {}", topic, payload, e);
        }
    }
}
