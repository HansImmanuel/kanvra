package com.kanvra.board.dto;

import com.kanvra.board.model.Board;

/**
 * Board representation (docs/SPEC.md §5).
 */
public record BoardResponse(Long id, Long projectId, String name, String status) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.getId(), board.getProjectId(), board.getName(), board.getStatus());
    }
}
