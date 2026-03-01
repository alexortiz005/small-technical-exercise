package com.eaortiz.producer.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a device that measures temperature. Has a lifecycle status; readings are validated
 * against the current status and optional range before updating room state.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Device {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double roomTemperature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum Status {
        PENDING, // Auto-registered from first MQTT message but has not yet sent a valid reading. No room state update.
        ACTIVE, // Sending readings within expected parameters. Room state updates processed normally.
        INACTIVE, // No reading within the configured time window. Next valid reading is accepted and transitions to ACTIVE.
        FAULTY, // Consecutive out-of-range readings exceeded threshold. Readings discarded; requires intervention to reset.
        DEREGISTERED, // Explicitly removed. Readings are silently rejected.
    }
}
