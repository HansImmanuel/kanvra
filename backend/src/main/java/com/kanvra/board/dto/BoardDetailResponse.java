package com.kanvra.board.dto;

import com.kanvra.task.dto.TaskSummary;
import java.util.List;

/**
 * Fully nested board response for initial rendering (docs/SPEC.md §5 and
 * Appendix A). Columns carry their ordered task cards.
 */
public record BoardDetailResponse(
        Long id,
        Long projectId,
        String name,
        String status,
        List<ColumnDetail> columns) {

    public record ColumnDetail(Long id, String name, Integer position, List<TaskSummary> tasks) {
    }
}
