package com.kanvra.kafka.deadletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Dead-letter row (docs/TECH_DOC.md §20). Consumers park messages that can
 * never succeed (malformed/structurally broken envelopes) here and ack them so
 * a poison pill cannot block its Kafka partition forever. The raw message is
 * preserved for manual inspection and offline reprocessing. No FK — it is an
 * ops/transport record, not a domain relationship.
 */
@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    /** Envelope {@code eventId}, when it was parseable (else null). */
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", length = 255)
    private String eventType;

    @Column(name = "raw_message", columnDefinition = "text")
    private String rawMessage;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}