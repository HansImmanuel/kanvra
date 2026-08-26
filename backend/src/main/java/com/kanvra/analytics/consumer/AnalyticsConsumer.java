package com.kanvra.analytics.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.analytics.model.AnalyticsEvent;
import com.kanvra.analytics.model.ProjectAnalytics;
import com.kanvra.analytics.repository.AnalyticsEventRepository;
import com.kanvra.analytics.repository.ProjectAnalyticsRepository;
import com.kanvra.kafka.deadletter.DeadLetterService;
import com.kanvra.kafka.event.KafkaEventTypes;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics Consumer (group {@code kanvra-analytics}, docs/SPEC.md §12.5/§14):
 * increments per-project counters for task/comment life-cycle events. Idempotent
 * via the unique {@code analytics_events.event_id} ledger row written in the
 * same transaction as the counter increment — a duplicate delivery cannot
 * double-count.
 *
 * <p>Failure split (TECH_DOC.md §20): malformed/structurally broken envelopes
 * are permanent and are parked in the dead-letter table then acked, so a poison
 * pill cannot block the partition forever; transient failures (DB/Kafka/service)
 * are rethrown so Kafka redelivers. Irrelevant event types are ignored.
 */
@Component
public class AnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);

    private final ProjectAnalyticsRepository analyticsRepository;
    private final AnalyticsEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final DeadLetterService deadLetterService;

    public AnalyticsConsumer(ProjectAnalyticsRepository analyticsRepository,
                             AnalyticsEventRepository eventRepository,
                             ObjectMapper objectMapper,
                             DeadLetterService deadLetterService) {
        this.analyticsRepository = analyticsRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.deadLetterService = deadLetterService;
    }

    @KafkaListener(topics = "kanvra.domain-events", groupId = "kanvra-analytics")
    @Transactional
    public void onDomainEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());
            long projectId = envelope.path("projectId").asLong();

            if (projectId == 0L || !isTracked(eventType)) {
                return; // not project-scoped or not a counted event
            }
            if (eventRepository.existsByEventId(eventId)) {
                return; // duplicate delivery, already counted
            }

            ProjectAnalytics row = analyticsRepository.findById(projectId)
                    .orElseGet(ProjectAnalytics::new);
            row.setProjectId(projectId);
            apply(row, eventType);

            analyticsRepository.save(row);
            // IDENTITY id forces the insert now; a duplicate racing past the
            // existsByEventId check trips the unique constraint here and the
            // whole transaction rolls back (counter included).
            eventRepository.save(new AnalyticsEvent(eventId, projectId));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent/duplicate delivery raced ahead of us — the unique
            // analytics_events.event_id constraint already counted this event.
            log.debug("Duplicate analytics event dropped: {}", message);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Structurally broken envelope (missing eventId, unparseable type) —
            // permanent, redelivery cannot fix it. Park + ack (TECH_DOC.md §20).
            log.error("Broken analytics event parked in dead-letter table: {}", message, ex);
            deadLetterService.record("kanvra-analytics", message, ex);
        } catch (IOException ex) {
            // Malformed JSON — permanent. Park + ack (TECH_DOC.md §20).
            log.error("Malformed analytics event parked in dead-letter table: {}", message, ex);
            deadLetterService.record("kanvra-analytics", message, ex);
        } catch (RuntimeException ex) {
            // Transient (DB down, etc.) — must NOT be acked: rethrow so Spring
            // Kafka redelivers the record (AGENT.md §12).
            log.error("Failed to process analytics event; letting Kafka redeliver: {}", message, ex);
            throw ex;
        }
    }

    private void apply(ProjectAnalytics row, String eventType) {
        switch (eventType) {
            case KafkaEventTypes.TASK_CREATED -> row.incrementTasksCreated();
            case KafkaEventTypes.TASK_COMPLETED -> row.incrementTasksCompleted();
            case KafkaEventTypes.TASK_MOVED -> row.incrementTasksMoved();
            case KafkaEventTypes.TASK_DELETED -> row.incrementTasksDeleted();
            case KafkaEventTypes.COMMENT_CREATED -> row.incrementCommentsCreated();
            default -> {
                // Not a counted event; the caller filters with isTracked().
            }
        }
    }

    private boolean isTracked(String eventType) {
        return switch (eventType) {
            case KafkaEventTypes.TASK_CREATED, KafkaEventTypes.TASK_COMPLETED, KafkaEventTypes.TASK_MOVED,
                    KafkaEventTypes.TASK_DELETED, KafkaEventTypes.COMMENT_CREATED -> true;
            default -> false;
        };
    }
}