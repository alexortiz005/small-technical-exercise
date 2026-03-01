package com.eaortiz.producer.mqtt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Payload for MQTT device update messages.
 * Expected JSON: {"deviceId": "uuid", "roomTemperature": 22.5}
 */
public record DeviceUpdatePayload(
        UUID deviceId,
        double roomTemperature
) {
    @JsonCreator
    public DeviceUpdatePayload(
            @JsonProperty("deviceId") UUID deviceId,
            @JsonProperty("roomTemperature") double roomTemperature) {
        this.deviceId = deviceId;
        this.roomTemperature = roomTemperature;
    }
}
