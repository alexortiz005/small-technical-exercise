package com.eaortiz.producer.service;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.eaortiz.producer.mqtt.DeviceUpdatePayload;
import org.springframework.stereotype.Service;

/**
 * Application service for device operations. Handles create-or-update from external updates (e.g. MQTT).
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * Creates a device or updates its room temperature if a device with the same logical key (name) exists.
     */
    public void createOrUpdate(DeviceUpdatePayload payload) {
        String name = payload.deviceId().toString();
        deviceRepository.findByName(name)
                .ifPresentOrElse(
                        device -> {
                            device.setRoomTemperature(payload.roomTemperature());
                            deviceRepository.save(device);
                        },
                        () -> {
                            Device device = Device.builder()
                                    .name(name)
                                    .roomTemperature(payload.roomTemperature())
                                    .build();
                            deviceRepository.save(device);
                        }
                );
    }
}
