package com.kanvra.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.notification.model.Notification;
import com.kanvra.notification.repository.NotificationRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification Consumer (group {@code kanvra-notification}, docs/SPEC.md §14 /
 * §12): turns a subset of domain events into user notifications. Idempotent via
 * the unique {@code (recipient_id, event_id)} constraint — a duplicate delivery
 * is detected and dropped. Malformed/unexpected events are rethrown so Kafka
 * redelivers them rather than silently acking (AGENT.md §12).
 *
 * <p>No PostgreSQL reads on the hot path: the denormalized {@code assigneeId} /
 * {@code authorId} members are expected to be present in the event payloads
 * (enriched at publish time, TECH_DOC.md §11 decision).
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "kanvra.domain-events", groupId = "kanvra-notification")
    @Transactional
    public void onDomainEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());
            JsonNode payload = envelope.path("payload");

            Notification n = buildNotification(eventType, eventId, payload);
            if (n == null) {
                return; // event irrelevant to any recipient
            }
            if (notificationRepository.existsByRecipientIdAndEventId(n.getRecipientId(), n.getEventId())) {
                return; // duplicate delivery for this recipient
            }
            notificationRepository.save(n);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent/duplicate delivery raced ahead of us — the unique
            // (recipient_id, event_id) constraint already recorded this one.
            log.debug("Duplicate notification event dropped: {}", message);
        } catch (IOException ex) {
            log.error("Malformed notification event; letting Kafka redeliver: {}", message, ex);
            throw new UncheckedIOException(ex);
        } catch (RuntimeException ex) {
            log.error("Failed to process notification event; letting Kafka redeliver: {}", message, ex);
            throw ex;
        }
    }

    /**
     * Maps an event to a notification, or {@code null} when the event has no
     * notification recipient.
     */
    private Notification buildNotification(String eventType, UUID eventId, JsonNode payload) {
        Notification n = new Notification();
        n.setEventId(eventId);

        return switch (eventType) {
            case "task.assigned", "task.completed", "comment.created" ->
                    taskNotification(n, eventType, payload);
            case "project.member_added" -> projectInvitation(n, payload);
            default -> null;
        };
    }

    private Notification taskNotification(Notification n, String eventType, JsonNode payload) {
        Long recipientId = asLongOrNull(payload, "assigneeId");
        if (recipientId == null) {
            return null;
        }
        n.setRecipientId(recipientId);
        String taskTitle = payload.path("taskTitle").asText("");
        String actorPrefix = actorPrefix(payload);
        switch (eventType) {
            case "task.assigned" -> {
                n.setType("TASK_ASSIGNED");
                n.setReferenceId(asLongOrNull(payload, "taskId"));
                n.setReferenceType("TASK");
                n.setMessage(actorPrefix + "assigned you the task '" + taskTitle + "'");
            }
            case "task.completed" -> {
                n.setType("TASK_COMPLETED");
                n.setReferenceId(asLongOrNull(payload, "taskId"));
                n.setReferenceType("TASK");
                n.setMessage(actorPrefix + "completed your task '" + taskTitle + "'");
            }
            case "comment.created" -> {
                Long authorId = asLongOrNull(payload, "commentAuthorId");
                if (authorId == null || authorId.equals(recipientId)) {
                    return null; // commenting on your own task isn't a notification
                }
                n.setType("TASK_COMMENTED");
                n.setReferenceId(asLongOrNull(payload, "commentId"));
                n.setReferenceType("COMMENT");
                n.setMessage(actorPrefix + "commented on your task '" + taskTitle + "'");
            }
            default -> {
                return null;
            }
        }
        return n;
    }

    private Notification projectInvitation(Notification n, JsonNode payload) {
        Long recipientId = asLongOrNull(payload, "userId");
        if (recipientId == null) {
            return null;
        }
        n.setRecipientId(recipientId);
        n.setType("PROJECT_INVITATION");
        n.setReferenceId(asLongOrNull(payload, "projectId"));
        n.setReferenceType("PROJECT");
        String projectName = payload.path("projectName").asText("a project");
        n.setMessage(actorPrefix(payload) + "added you to project '" + projectName + "'");
        return n;
    }

    private String actorPrefix(JsonNode payload) {
        String actor = payload.path("actorName").asText(null);
        return actor != null ? actor + " " : "";
    }

    private Long asLongOrNull(JsonNode payload, String field) {
        // Treat 0 as "absent" — our Longs are 1-based DB ids, so 0 is never valid.
        return payload.has(field) && payload.path(field).asLong(0L) != 0L
                ? payload.path(field).asLong()
                : null;
    }
}