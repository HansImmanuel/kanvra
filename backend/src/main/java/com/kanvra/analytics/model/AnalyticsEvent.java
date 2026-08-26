package com.kanvra.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency ledger for analytics counters (docs/SPEC.md §12.5). The unique
 * {@code event_id} mirrors {@code activities.event_id}: a duplicate Kafka
 * delivery is detected here — in the same transaction as the counter increment
 * — and dropped instead of double-counting.
 */
@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private UUID eventId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AnalyticsEvent() {
        // JPA
    }

    public AnalyticsEvent(UUID eventId, Long projectId) {
        this.eventId = eventId;
        this.projectId = projectId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}