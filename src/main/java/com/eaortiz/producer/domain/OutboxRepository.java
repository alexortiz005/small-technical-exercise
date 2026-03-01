package com.eaortiz.producer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for outbox entries. Used by the outbox publisher to poll PENDING entries.
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    List<OutboxEntry> findByStatusOrderByCreatedAtAsc(OutboxEntry.Status status);
}
