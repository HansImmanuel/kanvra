package com.kanvra.kafka.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event envelope (docs/TECH_DOC.md §10). Written to the transactional
 * outbox by domain services and reconstructed by the OutboxPublisher for Kafka.
 *
 * <p>{@code aggregateId} mirrors the underlying DB primary key (Long for MVP).
 * {@code eventId} is the stable UUID used for consumer-side deduplication.
 */
public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long actorId,
        Long projectId,
        String aggregateType,
        Long aggregateId,
        T payload) {

    public static <T> DomainEvent<T> of(
            String eventType, Long actorId, Long projectId, String aggregateType, Long aggregateId, T payload) {
        return new DomainEvent<>(UUID.randomUUID(), eventType, Instant.now(), actorId, projectId,
                aggregateType, aggregateId, payload);
    }
}
