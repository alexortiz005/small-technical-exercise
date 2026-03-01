package com.eaortiz.producer.service;

import com.eaortiz.producer.domain.Device;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Holds the latest device-state snapshot (from the last Kafka message consumed).
 * Always replaced as a whole; no per-device updates.
 */
@Service
public class DeviceSnapshotService {

    private volatile List<Device> snapshot = List.of();

    /**
     * Replaces the current snapshot with the given list (stored as an immutable copy).
     */
    public void replaceAll(List<Device> devices) {
        this.snapshot = List.copyOf(devices);
    }

    /**
     * Returns the current snapshot (copy so callers cannot mutate it).
     */
    public List<Device> getSnapshot() {
        return List.copyOf(snapshot);
    }
}
