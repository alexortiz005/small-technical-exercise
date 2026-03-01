package com.eaortiz.producer.mqtt;

import com.eaortiz.producer.domain.Device;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Payload for MQTT device update messages. Status is transmitted by the device; only allowed
 * state transitions are accepted.
 * Expected JSON: {"deviceId": "uuid", "name": "living-room", "roomTemperature": 22.5, "status": "ACTIVE"}
 */
public record DeviceUpdatePayload(
        UUID deviceId,
        String name,
        double roomTemperature,
        Device.Status status
) {
    @JsonCreator
    public DeviceUpdatePayload(
            @JsonProperty("deviceId") UUID deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("roomTemperature") double roomTemperature,
            @JsonProperty("status") Device.Status status) {
        this.deviceId = deviceId;
        this.name = name;
        this.roomTemperature = roomTemperature;
        this.status = status != null ? status : Device.Status.ACTIVE;
    }
}
