package com.eaortiz.producer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eaortiz.producer.outbox.OutboxEntry;
import com.eaortiz.producer.outbox.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final String TOPIC = "device-state";

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxRepository, TOPIC);
    }

    @Test
    @DisplayName("appendSnapshot deletes existing PENDING entries and saves new entry")
    void appendSnapshot_deletesPendingAndSavesNew() {
        // Given
        OutboxEntry oldPending = OutboxEntry.builder()
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("old")
                .status(OutboxEntry.Status.PENDING)
                .build();
        when(outboxRepository.findByStatus(OutboxEntry.Status.PENDING)).thenReturn(new ArrayList<>(List.of(oldPending)));

        // When
        outboxService.appendSnapshot("{\"devices\":[]}");

        // Then
        verify(outboxRepository).deleteAll(List.of(oldPending));
        ArgumentCaptor<OutboxEntry> saved = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getTopic()).isEqualTo(TOPIC);
        assertThat(saved.getValue().getPartitionKey()).isEqualTo("snapshot");
        assertThat(saved.getValue().getPayload()).isEqualTo("{\"devices\":[]}");
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxEntry.Status.PENDING);
    }

    @Test
    @DisplayName("appendSnapshot when no PENDING exists still saves new entry")
    void appendSnapshot_noPending_stillSaves() {
        // Given
        when(outboxRepository.findByStatus(OutboxEntry.Status.PENDING)).thenReturn(List.of());

        // When
        outboxService.appendSnapshot("[]");

        // Then
        verify(outboxRepository).deleteAll(List.of());
        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    @DisplayName("getPending when no PENDING returns empty")
    void getPending_noPending_returnsEmpty() {
        // Given
        when(outboxRepository.findByStatus(OutboxEntry.Status.PENDING)).thenReturn(List.of());

        // When
        Optional<OutboxEntry> result = outboxService.getPending();

        // Then
        assertThat(result).isEmpty();
        verify(outboxRepository).findByStatus(OutboxEntry.Status.PENDING);
    }

    @Test
    @DisplayName("getPending when one PENDING returns it")
    void getPending_onePending_returnsIt() {
        // Given
        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("{}")
                .status(OutboxEntry.Status.PENDING)
                .build();
        when(outboxRepository.findByStatus(OutboxEntry.Status.PENDING)).thenReturn(new ArrayList<>(List.of(entry)));

        // When
        Optional<OutboxEntry> result = outboxService.getPending();

        // Then
        assertThat(result).hasValue(entry);
        verify(outboxRepository).deleteAll(List.of());
    }

    @Test
    @DisplayName("getPending when multiple PENDING returns latest by createdAt and deletes others")
    void getPending_multiplePending_returnsLatestAndDeletesOthers() {

        // Given
        OutboxEntry older = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("old")
                .status(OutboxEntry.Status.PENDING)
                .createdAt(java.time.Instant.EPOCH)
                .build();
        OutboxEntry newer = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("new")
                .status(OutboxEntry.Status.PENDING)
                .createdAt(java.time.Instant.EPOCH.plusSeconds(1))
                .build();
        when(outboxRepository.findByStatus(OutboxEntry.Status.PENDING)).thenReturn(new ArrayList<>(List.of(older, newer)));

        // When
        Optional<OutboxEntry> result = outboxService.getPending();

        // Then
        assertThat(result).hasValue(newer);
        verify(outboxRepository).deleteAll(List.of(older));
    }

    @Test
    @DisplayName("markPublished sets status PUBLISHED and saves")
    void markPublished_setsPublishedAndSaves() {

        // Given
        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("{}")
                .status(OutboxEntry.Status.PENDING)
                .build();

        // When
        outboxService.markPublished(entry);

        // Then
        assertThat(entry.getStatus()).isEqualTo(OutboxEntry.Status.PUBLISHED);
        verify(outboxRepository).save(entry);
    }
}
