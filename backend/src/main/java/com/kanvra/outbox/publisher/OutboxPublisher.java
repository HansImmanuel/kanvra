package com.kanvra.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.kafka.config.KafkaConfig;
import com.kanvra.outbox.model.OutboxEvent;
import com.kanvra.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the transactional outbox and forwards unpublished rows to Kafka
 * (docs/TECH_DOC.md §8). Runs on a fixed schedule; failures are logged and the
 * row stays unpublished so the next poll retries — the DB is never asked to
 * publish synchronously (ADR-005).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPending() {
        List<OutboxEvent> pending = repository.findUnpublished();
        for (OutboxEvent row : pending) {
            try {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("eventId", row.getEventId().toString());
                envelope.put("eventType", row.getEventType());
                envelope.put("occurredAt", row.getCreatedAt());
                envelope.put("actorId", row.getActorId());
                envelope.put("projectId", row.getProjectId());
                envelope.put("aggregateType", row.getAggregateType());
                envelope.put("aggregateId", row.getAggregateId());
                envelope.put("payload", row.getPayload());

                String key = row.getProjectId() != null
                        ? "project:" + row.getProjectId()
                        : "aggregate:" + row.getAggregateId();

                kafkaTemplate.send(KafkaConfig.DOMAIN_EVENTS_TOPIC, key, objectMapper.writeValueAsString(envelope))
                        .get(10, TimeUnit.SECONDS);
                repository.markPublished(List.of(row.getId()), Instant.now());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event id={} type={}; will retry",
                        row.getId(), row.getEventType(), ex);
            }
        }
    }
}
