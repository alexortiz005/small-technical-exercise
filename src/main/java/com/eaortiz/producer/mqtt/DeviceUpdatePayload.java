package com.eaortiz.producer.mqtt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Payload for MQTT device update messages.
 * Expected JSON: {"deviceId": "uuid", "name": "living-room", "roomTemperature": 22.5}
 */
public record DeviceUpdatePayload(
        UUID deviceId,
        String name,
        double roomTemperature
) {
    @JsonCreator
    public DeviceUpdatePayload(
            @JsonProperty("deviceId") UUID deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("roomTemperature") double roomTemperature) {
        this.deviceId = deviceId;
        this.name = name;
        this.roomTemperature = roomTemperature;
    }
}
