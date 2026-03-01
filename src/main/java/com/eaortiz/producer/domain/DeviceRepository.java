package com.eaortiz.producer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * JPA repository for {@link Device} entity.
 */
public interface DeviceRepository extends JpaRepository<Device, UUID> {
}
