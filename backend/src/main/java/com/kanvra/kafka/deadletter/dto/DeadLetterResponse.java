package com.kanvra.kafka.deadletter.dto;

import com.kanvra.kafka.deadletter.DeadLetterEvent;
import java.time.Instant;

/**
 * Read model for the dead-letter inspection endpoint (docs/TECH_DOC.md §20).
 */
public record DeadLetterResponse(
        Long id,
        String consumerGroup,
        String eventId,
        String eventType,
        String reason,
        Instant createdAt) {

    public static DeadLetterResponse from(DeadLetterEvent row) {
        return new DeadLetterResponse(row.getId(), row.getConsumerGroup(), row.getEventId(),
                row.getEventType(), row.getReason(), row.getCreatedAt());
    }
}