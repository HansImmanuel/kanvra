package com.kanvra.board.dto;

import com.kanvra.board.model.BoardColumn;

/**
 * Column representation (docs/SPEC.md §6).
 */
public record ColumnResponse(Long id, Long boardId, String name, Integer position) {

    public static ColumnResponse from(BoardColumn column) {
        return new ColumnResponse(column.getId(), column.getBoardId(), column.getName(), column.getPosition());
    }
}
