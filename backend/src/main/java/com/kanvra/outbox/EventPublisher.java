package com.kanvra.outbox;

import com.kanvra.kafka.event.DomainEvent;

/**
 * Writes a domain event into the transactional outbox within the caller's
 * database transaction (docs/TECH_DOC.md §8, §11). Domain services must never
 * publish directly to Kafka; they call this instead.
 */
public interface EventPublisher {

    <T> void publish(DomainEvent<T> event);
}
