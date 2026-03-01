package com.eaortiz.producer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eaortiz.producer.domain.Device;
import com.eaortiz.producer.domain.DeviceRepository;
import com.eaortiz.producer.mqtt.DeviceUpdatePayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Set;

/**
 * Application service for device operations. Validates only state transitions: the payload
 * carries the desired status and the transition from current to that status must be allowed.
 * Uses the outbox pattern for the full state snapshot.
 */
@Service
@Slf4j
public class DeviceService {

    /** Allowed (from, to) status transitions. No transition from DEREGISTERED. */
    private static final Set<Device.Status> ALLOWED_FROM_PENDING = Set.of(Device.Status.ACTIVE);
    private static final Set<Device.Status> ALLOWED_FROM_ACTIVE = Set.of(Device.Status.ACTIVE, Device.Status.INACTIVE, Device.Status.FAULTY, Device.Status.DEREGISTERED);
    private static final Set<Device.Status> ALLOWED_FROM_INACTIVE = Set.of(Device.Status.ACTIVE);
    private static final Set<Device.Status> ALLOWED_FROM_FAULTY = Set.of(Device.Status.ACTIVE);

    private final DeviceRepository deviceRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public DeviceService(DeviceRepository deviceRepository,
                         OutboxService outboxService,
                         ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void createOrUpdate(DeviceUpdatePayload payload) {
        Optional<Device> device = deviceRepository.findById(payload.deviceId());
        Optional<Device> validated = validateTransitionAndPrepareDevice(payload, device);
        if (validated.isEmpty()) {
            return;
        }
        deviceRepository.save(validated.get());
        try {
            String payloadJson = objectMapper.writeValueAsString(deviceRepository.findAll());
            outboxService.appendSnapshot(payloadJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize device state snapshot", e);
        }
    }

    /**
     * Validates that the transition from current device status to payload status is allowed.
     * Does not fetch; only uses payload and given device. Returns empty if transition is disallowed.
     */
    private Optional<Device> validateTransitionAndPrepareDevice(DeviceUpdatePayload payload, Optional<Device> existing) {
        Device.Status currentStatus = existing.map(Device::getStatus).orElse(Device.Status.PENDING);
        Device.Status requestedStatus = payload.status();

        if (currentStatus == Device.Status.DEREGISTERED) {
            log.error("State transition rejected: device is DEREGISTERED, no further transitions allowed. deviceId={}, name={}, requestedStatus={}.",
                    payload.deviceId(), payload.name(), requestedStatus);
            return Optional.empty();
        }

        Set<Device.Status> allowedTargets = switch (currentStatus) {
            case PENDING -> ALLOWED_FROM_PENDING;
            case ACTIVE -> ALLOWED_FROM_ACTIVE;
            case INACTIVE -> ALLOWED_FROM_INACTIVE;
            case FAULTY -> ALLOWED_FROM_FAULTY;
            case DEREGISTERED -> Set.of(); // already handled above
        };

        if (!allowedTargets.contains(requestedStatus)) {
            log.error("State transition rejected: {} -> {} is not allowed. deviceId={}, name={}. Allowed from {}: {}.",
                    currentStatus, requestedStatus, payload.deviceId(), payload.name(), currentStatus, allowedTargets);
            return Optional.empty();
        }

        Device device = existing.orElseGet(() -> Device.builder()
                .id(payload.deviceId())
                .status(Device.Status.PENDING)
                .build());

        device.setName(payload.name());
        device.setRoomTemperature(payload.roomTemperature());
        device.setStatus(requestedStatus);
        return Optional.of(device);
    }
}
