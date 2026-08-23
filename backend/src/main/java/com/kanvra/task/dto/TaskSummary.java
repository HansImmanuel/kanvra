package com.kanvra.task.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Task card data embedded in the nested board response (docs/SPEC.md Appendix A).
 * {@code commentCount} is the active-comment count for the task, loaded by a
 * single grouped query in {@code TaskQueryService} (no N+1).
 */
public record TaskSummary(
        Long id,
        String title,
        String priority,
        Integer position,
        Integer version,
        AssigneeInfo assignee,
        List<LabelInfo> labels,
        LocalDate dueDate,
        long commentCount) {

    public record AssigneeInfo(Long id, String name, String avatarUrl) {
    }

    public record LabelInfo(Long id, String name, String color) {
    }
}
