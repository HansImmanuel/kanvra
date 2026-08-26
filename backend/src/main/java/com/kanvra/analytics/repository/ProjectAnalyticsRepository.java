package com.kanvra.analytics.repository;

import com.kanvra.analytics.model.ProjectAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Project counter rows (docs/SPEC.md §12.5). Reads are gated by project
 * membership in {@link com.kanvra.analytics.service.AnalyticsService}.
 */
public interface ProjectAnalyticsRepository extends JpaRepository<ProjectAnalytics, Long> {
}