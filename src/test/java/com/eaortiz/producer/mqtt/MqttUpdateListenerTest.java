package com.eaortiz.producer.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MqttUpdateListenerTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock
    private DeviceService deviceService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MqttUpdateListener listener;

    @Test
    @DisplayName("messageArrived when valid JSON deserializes and calls createOrUpdate")
    void validJson_callsCreateOrUpdate() throws Exception {
        // Given
        String payload = """
                {"deviceId":"%s","name":"living-room","roomTemperature":22.5,"status":"PENDING"}
                """.formatted(DEVICE_ID).trim();
        DeviceUpdatePayload update = new DeviceUpdatePayload(DEVICE_ID, "living-room", 22.5, Device.Status.PENDING);
        when(objectMapper.readValue(payload, DeviceUpdatePayload.class)).thenReturn(update);

        // When
        listener.messageArrived("devices/updates", new org.eclipse.paho.client.mqttv3.MqttMessage(payload.getBytes()));

        // Then
        verify(deviceService).createOrUpdate(argThat((Device d) ->
                d.getId().equals(DEVICE_ID) && d.getName().equals("living-room") && d.getRoomTemperature() == 22.5 && d.getStatus() == Device.Status.PENDING));
    }

    @Test
    @DisplayName("messageArrived when invalid JSON does not call createOrUpdate")
    void invalidJson_doesNotCallCreateOrUpdate() throws Exception {
        // Given
        String payload = "not json";
        when(objectMapper.readValue(payload, DeviceUpdatePayload.class)).thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad"));

        // When
        listener.messageArrived("devices/updates", new org.eclipse.paho.client.mqttv3.MqttMessage(payload.getBytes()));

        // Then
        verify(deviceService, never()).createOrUpdate(any());
    }
}
