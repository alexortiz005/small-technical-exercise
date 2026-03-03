package com.eaortiz.producer.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service for device operations. Accepts a {@link Device} (incoming state);
 * validates state transitions and persists. Uses the outbox pattern for the full state snapshot.
 */
@Service
@Slf4j
@AllArgsConstructor
public class DeviceService {

    /** Allowed (from, to) status transitions. No transition from DEREGISTERED. */
    private static final Set<Device.Status> ALLOWED_FROM_PENDING = Set.of(Device.Status.ACTIVE);
    private static final Set<Device.Status> ALLOWED_FROM_ACTIVE = Set.of(Device.Status.ACTIVE, Device.Status.INACTIVE, Device.Status.FAULTY, Device.Status.DEREGISTERED);
    private static final Set<Device.Status> ALLOWED_FROM_INACTIVE = Set.of(Device.Status.ACTIVE);
    private static final Set<Device.Status> ALLOWED_FROM_FAULTY = Set.of(Device.Status.ACTIVE);

    private final DeviceRepository deviceRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Creates or updates a device from the incoming device data. Validates state transitions;
     * if allowed, saves and appends the full device snapshot to the outbox.
     */
    @Transactional
    public void createOrUpdate(Device device) {
        Optional<Device> previous = deviceRepository.findById(device.getId());
        // if the previous device is not found, validate the new device, otherwise validate the transition
        boolean isValid = previous.map(value -> validateTransition(value, device)).orElseGet(() -> validateNewDevice(device));
        if (!isValid) {
            // if the device is not valid log the error and return
            log.warn("Device update rejected: validation failed. deviceId={}, name={}.", device.getId(), device.getName());
            return;
        }
        Device toSave = previous.map(p -> {
            p.setName(device.getName());
            p.setRoomTemperature(device.getRoomTemperature());
            p.setStatus(device.getStatus());
            return p;
        }).orElse(device);
        deviceRepository.save(toSave);
        try {
            String payloadJson = objectMapper.writeValueAsString(deviceRepository.findAll());
            outboxService.appendSnapshot(payloadJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize device state snapshot", e);
        }
    }

    /**
     * Validates that a new device (no previous record) can be created. Only PENDING status is allowed for new devices.
     */
    private boolean validateNewDevice(Device device) {
        if (device.getStatus() != Device.Status.PENDING) {
            log.warn("New device validation failed: status must be PENDING. deviceId={}, name={}, status={}.",
                    device.getId(), device.getName(), device.getStatus());
            return false;
        }
        return true;
    }

    /**
     * Validates that the transition from the previous device status to the new device's status is allowed.
     * Returns true if the transition is allowed, false otherwise. No side effects.
     */
    private boolean validateTransition(Device previous, Device newDevice) {
        Device.Status currentStatus = previous.getStatus();
        Device.Status requestedStatus = newDevice.getStatus();

        if (currentStatus == Device.Status.DEREGISTERED) {
            log.warn("State transition rejected: device is DEREGISTERED, no further transitions allowed. deviceId={}, name={}, requestedStatus={}.",
                    newDevice.getId(), newDevice.getName(), requestedStatus);
            return false;
        }

        Set<Device.Status> allowedTargets = switch (currentStatus) {
            case PENDING -> ALLOWED_FROM_PENDING;
            case ACTIVE -> ALLOWED_FROM_ACTIVE;
            case INACTIVE -> ALLOWED_FROM_INACTIVE;
            case FAULTY -> ALLOWED_FROM_FAULTY;
            case DEREGISTERED -> Set.of();
        };

        if (!allowedTargets.contains(requestedStatus)) {
            log.warn("State transition rejected: {} -> {} is not allowed. deviceId={}, name={}. Allowed from {}: {}.",
                    currentStatus, requestedStatus, newDevice.getId(), newDevice.getName(), currentStatus, allowedTargets);
            return false;
        }

        return true;
    }
}
