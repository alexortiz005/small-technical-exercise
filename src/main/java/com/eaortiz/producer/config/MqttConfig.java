package com.eaortiz.producer.config;

import com.eaortiz.producer.mqtt.MqttUpdateListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

/**
 * Configures MQTT client and subscribes to the device updates topic when enabled.
 * Connects after application startup so a down broker does not block startup.
 */
@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.topic-updates}")
    private String topicUpdates;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    private final MqttClient mqttClient;
    private final MqttUpdateListener updateListener;

    public MqttConfig(@Lazy MqttClient mqttClient, MqttUpdateListener updateListener) {
        this.mqttClient = mqttClient;
        this.updateListener = updateListener;
    }

    @Bean
    public MqttClient mqttClient() throws MqttException {
        return new MqttClient(brokerUrl, clientId, new MemoryPersistence());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribeWhenReady(ApplicationReadyEvent event) {
        if (topicUpdates == null || topicUpdates.isBlank()) {
            log.warn("MQTT topic-updates is not set. Set mqtt.topic-updates=devices/updates in configuration.");
            return;
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
            if (password != null) {
                options.setPassword(password.toCharArray());
            }
        }
        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect(options);
            }
            mqttClient.subscribe(topicUpdates, updateListener);
            log.info("MQTT connected to {} and subscribed to {}", brokerUrl, topicUpdates);
        } catch (MqttException e) {
            log.warn("MQTT connection failed: {}. Set mqtt.enabled=false to disable.", e.getMessage());
        }
    }
}
