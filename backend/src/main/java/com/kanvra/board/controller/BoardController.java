package com.kanvra.board.controller;

import com.kanvra.board.dto.BoardDetailResponse;
import com.kanvra.board.dto.BoardRequest;
import com.kanvra.board.dto.BoardResponse;
import com.kanvra.board.dto.ColumnRequest;
import com.kanvra.board.dto.ColumnResponse;
import com.kanvra.board.dto.ReorderColumnsRequest;
import com.kanvra.board.service.BoardService;
import com.kanvra.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Board and column API (docs/SPEC.md §5, §6).
 */
@RestController
public class BoardController {

    private final BoardService boardService;
    private final CurrentUser currentUser;

    public BoardController(BoardService boardService, CurrentUser currentUser) {
        this.boardService = boardService;
        this.currentUser = currentUser;
    }

    // --- Boards ------------------------------------------------------------

    @PostMapping("/api/v1/projects/{projectId}/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse createBoard(@PathVariable Long projectId, @Valid @RequestBody BoardRequest request) {
        return boardService.create(currentUser.require().id(), projectId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/boards")
    public List<BoardResponse> listBoards(@PathVariable Long projectId) {
        return boardService.list(currentUser.require().id(), projectId);
    }

    @GetMapping("/api/v1/boards/{boardId}")
    public BoardDetailResponse getBoard(@PathVariable Long boardId) {
        return boardService.get(currentUser.require().id(), boardId);
    }

    @PatchMapping("/api/v1/boards/{boardId}")
    public BoardResponse updateBoard(@PathVariable Long boardId, @Valid @RequestBody BoardRequest request) {
        return boardService.update(currentUser.require().id(), boardId, request);
    }

    @PostMapping("/api/v1/boards/{boardId}/archive")
    public BoardResponse archiveBoard(@PathVariable Long boardId) {
        return boardService.archive(currentUser.require().id(), boardId);
    }

    // --- Columns ------------------------------------------------------------

    @PostMapping("/api/v1/boards/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse createColumn(@PathVariable Long boardId, @Valid @RequestBody ColumnRequest request) {
        return boardService.createColumn(currentUser.require().id(), boardId, request);
    }

    @PostMapping("/api/v1/boards/{boardId}/columns/reorder")
    public List<ColumnResponse> reorderColumns(@PathVariable Long boardId,
                                               @Valid @RequestBody ReorderColumnsRequest request) {
        return boardService.reorderColumns(currentUser.require().id(), boardId, request);
    }

    @PatchMapping("/api/v1/columns/{columnId}")
    public ColumnResponse updateColumn(@PathVariable Long columnId, @Valid @RequestBody ColumnRequest request) {
        return boardService.updateColumn(currentUser.require().id(), columnId, request);
    }

    @DeleteMapping("/api/v1/columns/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(@PathVariable Long columnId, @RequestParam(required = false) Long targetColumnId) {
        boardService.deleteColumn(currentUser.require().id(), columnId, targetColumnId);
    }
}
