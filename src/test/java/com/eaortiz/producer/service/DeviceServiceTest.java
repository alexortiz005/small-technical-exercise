package com.eaortiz.producer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    @DisplayName("createOrUpdate new device with PENDING saves and appends outbox")
    void newDevicePending_savesAndAppendsOutbox() throws JsonProcessingException {
        // Given
        Device incoming = Device.builder()
                .id(DEVICE_ID)
                .name("living-room")
                .roomTemperature(22.5)
                .status(Device.Status.PENDING)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceRepository.findAll()).thenReturn(List.of(incoming));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        deviceService.createOrUpdate(incoming);

        // Then
        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(DEVICE_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo(Device.Status.PENDING);
        verify(outboxService).appendSnapshot(anyString());
    }

    @Test
    @DisplayName("createOrUpdate new device with non-PENDING rejects and does not save")
    void newDeviceNonPending_rejects() {
        // Given
        Device incoming = Device.builder()
                .id(DEVICE_ID)
                .name("living-room")
                .roomTemperature(22.5)
                .status(Device.Status.ACTIVE)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        // When
        deviceService.createOrUpdate(incoming);

        // Then
        verify(deviceRepository, never()).save(any());
        verify(outboxService, never()).appendSnapshot(anyString());
    }

    @Test
    @DisplayName("createOrUpdate PENDING to ACTIVE updates and appends outbox")
    void pendingToActive_updatesAndAppendsOutbox() throws JsonProcessingException {
        // Given
        Device existing = Device.builder()
                .id(DEVICE_ID)
                .name("old")
                .roomTemperature(20.0)
                .status(Device.Status.PENDING)
                .build();
        Device incoming = Device.builder()
                .id(DEVICE_ID)
                .name("kitchen")
                .roomTemperature(21.5)
                .status(Device.Status.ACTIVE)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));
        when(deviceRepository.findAll()).thenReturn(List.of(existing));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        deviceService.createOrUpdate(incoming);

        // Then
        verify(deviceRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("kitchen");
        assertThat(existing.getRoomTemperature()).isEqualTo(21.5);
        assertThat(existing.getStatus()).isEqualTo(Device.Status.ACTIVE);
        verify(outboxService).appendSnapshot(anyString());
    }

    @Test
    @DisplayName("createOrUpdate PENDING to FAULTY rejects and does not save")
    void pendingToFaulty_rejects() {
        // Given: existing PENDING device and incoming update to FAULTY (invalid transition)
        Device existing = Device.builder()
                .id(DEVICE_ID)
                .name("garage")
                .roomTemperature(18.0)
                .status(Device.Status.PENDING)
                .build();
        Device incoming = Device.builder()
                .id(DEVICE_ID)
                .name("garage")
                .roomTemperature(36.0)
                .status(Device.Status.FAULTY)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));
        // When: createOrUpdate(incoming)
        deviceService.createOrUpdate(incoming);
        // Then: no save, no outbox append
        verify(deviceRepository, never()).save(any());
        verify(outboxService, never()).appendSnapshot(anyString());
    }

    @Test
    @DisplayName("createOrUpdate DEREGISTERED device rejects any transition")
    void deregistered_rejects() {
        // Given: existing DEREGISTERED device and incoming update to ACTIVE
        Device existing = Device.builder()
                .id(DEVICE_ID)
                .name("old")
                .roomTemperature(20.0)
                .status(Device.Status.DEREGISTERED)
                .build();
        Device incoming = Device.builder()
                .id(DEVICE_ID)
                .name("new")
                .roomTemperature(21.0)
                .status(Device.Status.ACTIVE)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));
        // When: createOrUpdate(incoming)
        deviceService.createOrUpdate(incoming);
        // Then: no save, no outbox append
        verify(deviceRepository, never()).save(any());
        verify(outboxService, never()).appendSnapshot(anyString());
    }

    @Test
    @DisplayName("createOrUpdate when snapshot serialization fails throws IllegalStateException")
    void serializationFailure_throws() throws JsonProcessingException {
        // Given: valid new device, but ObjectMapper throws on writeValueAsString
        Device incoming = Device.builder()
                .id(DEVICE_ID)
                .name("living-room")
                .roomTemperature(22.5)
                .status(Device.Status.PENDING)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceRepository.findAll()).thenReturn(List.of(incoming));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fail") {});
        // When: createOrUpdate(incoming)
        // Then: throws IllegalStateException with cause
        assertThatThrownBy(() -> deviceService.createOrUpdate(incoming))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize device state snapshot")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }
}
