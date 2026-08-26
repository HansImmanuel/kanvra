package com.kanvra.analytics.dto;

import com.kanvra.analytics.model.ProjectAnalytics;
import java.util.List;

/**
 * Project analytics payload (docs/SPEC.md §12.5). {@code counters} are
 * event-derived cumulative totals accumulated by the Analytics Consumer;
 * {@code cardsPerColumn} is derived live from the authoritative board state.
 */
public record ProjectAnalyticsResponse(
        Long projectId,
        ProjectCounters counters,
        List<CardsPerColumn> cardsPerColumn) {

    public static final ProjectCounters ZERO_COUNTERS = new ProjectCounters(0, 0, 0, 0, 0);

    public record ProjectCounters(long tasksCreated, long tasksCompleted, long tasksMoved,
                                  long tasksDeleted, long commentsCreated) {

        public static ProjectCounters from(ProjectAnalytics row) {
            return new ProjectCounters(row.getTasksCreated(), row.getTasksCompleted(), row.getTasksMoved(),
                    row.getTasksDeleted(), row.getCommentsCreated());
        }
    }

    public record CardsPerColumn(Long columnId, String columnName, int position, long count) {
    }
}