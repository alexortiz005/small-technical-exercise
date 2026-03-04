package com.eaortiz.producer.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.eaortiz.producer.outbox.OutboxEntry;
import com.eaortiz.producer.service.OutboxService;

@ExtendWith(MockitoExtension.class)
class KafkaUpdatePublisherTest {

    private static final String TOPIC = "device-state";

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaUpdatePublisher publisher;

    @Test
    @DisplayName("publishPending when no PENDING does nothing")
    void noPending_doesNothing() {
        // Given
        when(outboxService.getPending()).thenReturn(Optional.empty());

        // When
        publisher.publishPending();

        // Then
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(outboxService, never()).markPublished(any());
    }

    @Test
    @DisplayName("publishPending when PENDING exists sends to Kafka and marks published")
    void pendingExists_sendsAndMarksPublished() {
        // Given
        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("{}")
                .status(OutboxEntry.Status.PENDING)
                .build();
        when(outboxService.getPending()).thenReturn(Optional.of(entry));
        when(kafkaTemplate.send(eq(TOPIC), eq("snapshot"), eq("{}"))).thenReturn(CompletableFuture.completedFuture(null));

        // When
        publisher.publishPending();

        // Then
        verify(kafkaTemplate).send(TOPIC, "snapshot", "{}");
        verify(outboxService).markPublished(entry);
    }

    @Test
    @DisplayName("publishPending when send fails does not mark published")
    void sendFails_doesNotMarkPublished() {
        // Given
        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .topic(TOPIC)
                .partitionKey("snapshot")
                .payload("{}")
                .status(OutboxEntry.Status.PENDING)
                .build();
        when(outboxService.getPending()).thenReturn(Optional.of(entry));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        // When
        publisher.publishPending();

        // Then
        verify(kafkaTemplate).send(any(), any(), any());
        verify(outboxService, never()).markPublished(any());
    }
}
