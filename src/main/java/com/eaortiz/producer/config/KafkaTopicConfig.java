package com.eaortiz.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates the device-state Kafka topic with log compaction so only the latest snapshot per key is retained.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic deviceStateTopic(@Value("${kafka.topic.device-state}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .compact()
                .build();
    }
}
