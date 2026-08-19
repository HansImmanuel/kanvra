package com.kanvra.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic provisioning (docs/TECH_DOC.md §9). A single MVP topic,
 * {@code kanvra.domain-events}, created at startup via KafkaAdmin.
 */
@Configuration
public class KafkaConfig {

    public static final String DOMAIN_EVENTS_TOPIC = "kanvra.domain-events";

    @Bean
    public NewTopic domainEventsTopic() {
        return TopicBuilder.name(DOMAIN_EVENTS_TOPIC).partitions(1).replicas(1).build();
    }
}
