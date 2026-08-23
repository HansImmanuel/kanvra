package com.kanvra.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.kafka.config.KafkaConfig;
import com.kanvra.outbox.model.OutboxEvent;
import com.kanvra.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
 *
 * <p>Backoff (Sprint 4 hardening): consecutive publish failures arm an
 * exponential backoff with ±20% jitter (bounded at {@link #MAX_DELAY_MS}) so a
 * down broker is not hammered every 500ms. Success resets the counter.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    public static final long BASE_DELAY_MS = 500;
    public static final long MAX_DELAY_MS = 30_000;
    static final int MAX_BACKOFF_EXPONENT = 6;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private int consecutiveFailures = 0;
    private volatile Instant nextAttemptAt = Instant.EPOCH;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPending() {
        if (Instant.now().isBefore(nextAttemptAt)) {
            // In backoff after a broker outage; skip this tick without touching
            // the broker (no point polling rows we cannot publish).
            return;
        }

        // Bounded batch: load only the oldest 100 unpublished rows per tick so a
        // backlog or a down broker cannot balloon memory / block the scheduler
        // indefinitely. Rows stay unpublished on failure and are retried.
        List<OutboxEvent> pending = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        if (pending.isEmpty()) {
            consecutiveFailures = 0;
            nextAttemptAt = Instant.EPOCH;
            return;
        }

        boolean anyFailure = false;
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
                anyFailure = true;
            }
        }

        if (anyFailure) {
            consecutiveFailures++;
            scheduleBackoff();
        } else {
            consecutiveFailures = 0;
            nextAttemptAt = Instant.EPOCH;
        }
    }

    private void scheduleBackoff() {
        long delay = computeBackoffMillis(consecutiveFailures);
        nextAttemptAt = Instant.now().plusMillis(delay);
        log.warn("Outbox publish failures consecutive={}; backing off {}ms before the next poll",
                consecutiveFailures, delay);
    }

    /**
     * Exponential backoff with ±20% jitter, bounded at {@link #MAX_DELAY_MS}.
     * Exposed for deterministic unit tests.
     */
    static long computeBackoffMillis(int consecutiveFailures) {
        long exponent = Math.min(consecutiveFailures, MAX_BACKOFF_EXPONENT);
        long base = Math.min(BASE_DELAY_MS * (1L << exponent), MAX_DELAY_MS);
        double jitter = 0.8 + 0.4 * ThreadLocalRandom.current().nextDouble();
        // Clamp AFTER jitter so MAX_DELAY_MS is a true hard ceiling.
        return Math.max(1L, Math.min((long) (base * jitter), MAX_DELAY_MS));
    }

    /** Test/observability accessor. */
    int consecutiveFailures() {
        return consecutiveFailures;
    }
}
