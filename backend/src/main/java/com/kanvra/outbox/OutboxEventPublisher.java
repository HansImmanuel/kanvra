package com.kanvra.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.kafka.event.DomainEvent;
import com.kanvra.outbox.model.OutboxEvent;
import com.kanvra.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EventPublisher} implementation that appends an {@code outbox_events}
 * row inside the current database transaction (ADR-005, TECH_DOC.md §8).
 * {@code Propagation.MANDATORY} ensures it is only ever used from within a
 * domain transaction, so the state change and its event commit atomically.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> void publish(DomainEvent<T> event) {
        OutboxEvent row = new OutboxEvent();
        row.setEventId(event.eventId());
        row.setEventType(event.eventType());
        row.setAggregateType(event.aggregateType());
        row.setAggregateId(event.aggregateId());
        row.setProjectId(event.projectId());
        row.setActorId(event.actorId());
        row.setPayload(objectMapper.valueToTree(event.payload()));
        repository.save(row);
    }
}
