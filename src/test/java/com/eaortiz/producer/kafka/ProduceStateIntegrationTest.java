package com.eaortiz.producer.kafka;

import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import com.eaortiz.producer.service.OutboxService;


@SpringBootTest(properties = "mqtt.enabled=false")
@EmbeddedKafka(topics = "device-state", partitions = 1)
@DirtiesContext
class ProduceStateIntegrationTest {

    private static final String DEVICE_STATE_TOPIC = "device-state";

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private KafkaUpdatePublisher kafkaUpdatePublisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private DefaultKafkaConsumerFactory<String, String> consumerFactory;

    @BeforeEach
    void setUp() {
        // Given
        // the embedded Kafka broker is setup
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("kafka-integration-test", "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
    }

    @Test
    @DisplayName("Given a pending outbox entry, when the publisher runs, then the message is sent to Kafka and the entry is marked published")
    void whenOutboxHasPendingEntry_thenMessageIsSentToEmbeddedKafka() {
        // Given
        // the outbox has one pending device-state snapshot
        String payload = "[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"sensor-1\",\"roomTemperature\":21.0,\"status\":\"ACTIVE\"}]";
        outboxService.appendSnapshot(payload);

        // When
        // the outbox publisher runs
        kafkaUpdatePublisher.publishPending();

        // Then
        // the message is on the embedded Kafka topic
        try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, DEVICE_STATE_TOPIC);
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, DEVICE_STATE_TOPIC);
            assertThat(record).isNotNull();
            assertThat(record.value()).isEqualTo(payload);
            assertThat(record.key()).isEqualTo("snapshot");
        } catch (Exception e) {
            throw new AssertionError("Failed to consume or assert Kafka record", e);
        }
    }
}
