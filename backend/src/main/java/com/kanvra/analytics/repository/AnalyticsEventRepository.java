package com.kanvra.analytics.repository;

import com.kanvra.analytics.model.AnalyticsEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Idempotency ledger for the Analytics Consumer (docs/SPEC.md §12.5).
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    boolean existsByEventId(UUID eventId);
}