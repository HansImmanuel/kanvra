package com.kanvra.board.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * POST /api/v1/boards/{boardId}/columns/reorder body (docs/SPEC.md §6). The
 * list must be a permutation of the board's column ids.
 */
public record ReorderColumnsRequest(
        @NotEmpty List<Long> columnIds) {
}
