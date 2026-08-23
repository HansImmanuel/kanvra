package com.kanvra.realtime.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.kafka.config.KafkaConfig;
import com.kanvra.kafka.deadletter.DeadLetterService;
import com.kanvra.realtime.dto.RealtimeMessage;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Realtime Consumer (group {@code kanvra-realtime}, docs/SPEC.md §14 / §15):
 * broadcasts UI-focused board messages to {@code /topic/projects/{projectId}}.
 * Each message carries the originating {@code eventId} so the frontend can
 * ignore its own optimistic echo (SPEC §15.3), and the denormalized payload
 * flattened at top level (no PostgreSQL reads on the hot path).
 *
 * <p>Realtime delivery is best-effort by design; the authoritative recovery is
 * the client re-fetching the board on reconnect (SPEC §15.2), not this stream.
 * Malformed/structurally broken envelopes are parked in the dead-letter table
 * and acked; transient failures (e.g. broker down) are rethrown so Kafka
 * redelivers — a duplicate broadcast is harmless (TECH_DOC.md §20).
 */
@Component
public class RealtimeConsumer {

    private static final Logger log = LoggerFactory.getLogger(RealtimeConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final DeadLetterService deadLetterService;

    public RealtimeConsumer(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper,
                            DeadLetterService deadLetterService) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.deadLetterService = deadLetterService;
    }

    @KafkaListener(topics = KafkaConfig.DOMAIN_EVENTS_TOPIC, groupId = "kanvra-realtime")
    public void onDomainEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            JsonNode payload = envelope.path("payload");

            if (!envelope.hasNonNull("projectId") || envelope.path("projectId").asLong() == 0L) {
                return; // not project-scoped, nothing to broadcast
            }
            Long projectId = envelope.path("projectId").asLong();

            RealtimeMessage out = RealtimeMessage.from(eventType, envelope.path("eventId").asText(), projectId, payload);
            if (out == null) {
                return; // not a realtime-relevant event
            }

            messagingTemplate.convertAndSend("/topic/projects/" + projectId, out);
        } catch (IOException ex) {
            // Malformed JSON — permanent. Park + ack (TECH_DOC.md §20).
            log.error("Malformed realtime event parked in dead-letter table: {}", message, ex);
            deadLetterService.record("kanvra-realtime", message, ex);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Structurally broken envelope (missing payload/fields) — permanent.
            // Park + ack so it cannot block the partition (TECH_DOC.md §20).
            log.error("Broken realtime event parked in dead-letter table: {}", message, ex);
            deadLetterService.record("kanvra-realtime", message, ex);
        } catch (RuntimeException ex) {
            log.error("Realtime broadcast failed; letting Kafka redeliver: {}", message, ex);
            throw ex;
        }
    }
}