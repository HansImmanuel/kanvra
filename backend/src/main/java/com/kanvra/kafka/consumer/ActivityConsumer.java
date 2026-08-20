package com.kanvra.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.activity.model.Activity;
import com.kanvra.activity.repository.ActivityRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activity Consumer (group {@code kanvra-activity}, docs/SPEC.md §14): writes
 * user-readable activity records from denormalized event payloads. Idempotent
 * via the unique {@code activities.event_id} constraint — a duplicate delivery
 * is detected and dropped instead of creating a second record.
 */
@Component
public class ActivityConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActivityConsumer.class);

    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    public ActivityConsumer(ActivityRepository activityRepository, ObjectMapper objectMapper) {
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "kanvra.domain-events", groupId = "kanvra-activity")
    @Transactional
    public void onDomainEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());

            if (activityRepository.existsByEventId(eventId)) {
                return; // duplicate delivery, already processed
            }

            Activity activity = new Activity();
            activity.setEventId(eventId);
            activity.setActorId(envelope.hasNonNull("actorId") ? envelope.get("actorId").asLong() : null);
            activity.setProjectId(envelope.hasNonNull("projectId") ? envelope.get("projectId").asLong() : null);
            activity.setType(toActivityType(eventType));
            activity.setMessage(buildMessage(eventType, envelope.path("payload")));
            activityRepository.save(activity);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent/duplicate delivery raced ahead of us — the unique
            // activities.event_id constraint already recorded this event.
            log.debug("Duplicate activity event dropped: {}", message);
        } catch (IOException ex) {
            // Malformed envelope — not a duplicate, so it must not be silently
            // acked. Let Kafka redeliver (a DLT is deferred, TECH_DOC.md §20).
            log.error("Malformed activity event; letting Kafka redeliver: {}", message, ex);
            throw new UncheckedIOException(ex);
        } catch (RuntimeException ex) {
            // Any other failure must NOT be silently acked: rethrow so Spring
            // Kafka redelivers the record (AGENT.md §12 — do not swallow Kafka
            // errors). Redelivery + idempotency is the retry fabric for now.
            log.error("Failed to process activity event; letting Kafka redeliver: {}", message, ex);
            throw ex;
        }
    }

    private String toActivityType(String eventType) {
        return eventType.replace('.', '_').toUpperCase();
    }

    private String buildMessage(String eventType, JsonNode payload) {
        String actor = payload.path("actorName").asText(null);
        String actorPrefix = actor != null ? actor + " " : "";

        return switch (eventType) {
            case "task.created" -> actorPrefix + "created task '" + payload.path("taskTitle").asText("") + "'";
            case "task.updated" -> actorPrefix + "updated task '" + payload.path("taskTitle").asText("") + "'";
            case "task.deleted" -> actorPrefix + "deleted task '" + payload.path("taskTitle").asText("") + "'";
            case "task.moved" -> actorPrefix + "moved task '" + payload.path("taskTitle").asText("")
                    + "' from " + payload.path("fromColumnName").asText("?")
                    + " to " + payload.path("toColumnName").asText("?");
            case "task.assigned" -> actorPrefix + "assigned task '" + payload.path("taskTitle").asText("") + "'";
            case "task.unassigned" -> actorPrefix + "unassigned task '" + payload.path("taskTitle").asText("") + "'";
            case "task.completed" -> actorPrefix + "completed task '" + payload.path("taskTitle").asText("") + "'";
            case "project.created" -> actorPrefix + "created project '" + payload.path("name").asText("") + "'";
            case "project.archived" -> actorPrefix + "archived project '" + payload.path("name").asText("") + "'";
            case "project.member_added" -> actorPrefix + "added a member to the project";
            default -> eventType;
        };
    }
}
