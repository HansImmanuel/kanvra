package com.kanvra.board.service;

import com.kanvra.board.dto.BoardDetailResponse;
import com.kanvra.board.dto.BoardRequest;
import com.kanvra.board.dto.BoardResponse;
import com.kanvra.board.dto.ColumnRequest;
import com.kanvra.board.dto.ColumnResponse;
import com.kanvra.board.dto.ReorderColumnsRequest;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.common.error.ColumnNotEmptyException;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.dto.TaskQueryService;
import com.kanvra.task.dto.TaskSummary;
import com.kanvra.task.model.Task;
import com.kanvra.task.repository.TaskRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Boards and columns (docs/SPEC.md §5, §6). Board creation is open to any
 * project member; column deletion handles the "move tasks to targetColumnId
 * or 409 COLUMN_NOT_EMPTY" behavior from §6.
 */
@Service
public class BoardService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final ProjectAccessService access;
    private final TaskQueryService taskQueryService;
    private final TaskRepository taskRepository;

    public BoardService(BoardRepository boardRepository, BoardColumnRepository columnRepository,
                        ProjectAccessService access, TaskQueryService taskQueryService,
                        TaskRepository taskRepository) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.access = access;
        this.taskQueryService = taskQueryService;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public BoardResponse create(Long userId, Long projectId, BoardRequest request) {
        access.requireMembership(userId, projectId);
        Board board = new Board();
        board.setProjectId(projectId);
        board.setName(request.name().trim());
        board.setStatus(STATUS_ACTIVE);
        return BoardResponse.from(boardRepository.save(board));
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> list(Long userId, Long projectId) {
        access.requireMembership(userId, projectId);
        return boardRepository.findByProjectId(projectId).stream().map(BoardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse get(Long userId, Long boardId) {
        Board board = requireBoard(userId, boardId);
        List<BoardColumn> columns = columnRepository.findByBoardIdOrderByPositionAsc(boardId);
        List<Long> columnIds = columns.stream().map(BoardColumn::getId).toList();
        Map<Long, List<TaskSummary>> tasksByColumn = taskQueryService.summariesByColumn(columnIds);
        List<BoardDetailResponse.ColumnDetail> columnDetails = columns.stream()
                .map(c -> new BoardDetailResponse.ColumnDetail(
                        c.getId(), c.getName(), c.getPosition(),
                        tasksByColumn.getOrDefault(c.getId(), List.of())))
                .toList();
        return new BoardDetailResponse(board.getId(), board.getProjectId(), board.getName(), board.getStatus(),
                columnDetails);
    }

    @Transactional
    public BoardResponse update(Long userId, Long boardId, BoardRequest request) {
        Board board = requireBoard(userId, boardId);
        board.setName(request.name().trim());
        return BoardResponse.from(board);
    }

    @Transactional
    public BoardResponse archive(Long userId, Long boardId) {
        Board board = requireBoard(userId, boardId);
        if (!STATUS_ARCHIVED.equals(board.getStatus())) {
            board.setStatus(STATUS_ARCHIVED);
        }
        return BoardResponse.from(board);
    }

    // ---------------------------------------------------------------
    // Columns
    // ---------------------------------------------------------------

    @Transactional
    public ColumnResponse createColumn(Long userId, Long boardId, ColumnRequest request) {
        Board board = requireBoard(userId, boardId);
        List<BoardColumn> existing = columnRepository.findByBoardIdOrderByPositionAsc(boardId);
        int maxPosition = existing.stream().mapToInt(BoardColumn::getPosition).max().orElse(-1);

        BoardColumn column = new BoardColumn();
        column.setBoardId(board.getId());
        column.setName(request.name().trim());
        column.setPosition(maxPosition + 1);
        return ColumnResponse.from(columnRepository.save(column));
    }

    @Transactional
    public ColumnResponse updateColumn(Long userId, Long columnId, ColumnRequest request) {
        BoardColumn column = requireColumn(userId, columnId);
        column.setName(request.name().trim());
        return ColumnResponse.from(column);
    }

    @Transactional
    public void deleteColumn(Long userId, Long columnId, Long targetColumnId) {
        BoardColumn column = requireColumn(userId, columnId);
        List<Task> tasks = taskRepository.lockTasksInColumn(columnId);
        if (!tasks.isEmpty() && targetColumnId == null) {
            throw new ColumnNotEmptyException("Column has tasks; provide targetColumnId to move them before deleting");
        }
        if (!tasks.isEmpty()) {
            requireColumn(userId, targetColumnId);
            List<Task> targetTasks = taskRepository.lockTasksInColumn(targetColumnId);
            int base = targetTasks.stream().mapToInt(Task::getPosition).max().orElse(-1);
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).setColumnId(targetColumnId);
                tasks.get(i).setPosition(base + 1 + i);
            }
            taskRepository.saveAll(tasks);
        }
        columnRepository.delete(column);
    }

    @Transactional
    public List<ColumnResponse> reorderColumns(Long userId, Long boardId, ReorderColumnsRequest request) {
        requireBoard(userId, boardId);
        List<BoardColumn> columns = columnRepository.findByBoardIdOrderByPositionAsc(boardId);
        Map<Long, BoardColumn> byId = columns.stream()
                .collect(Collectors.toMap(BoardColumn::getId, Function.identity()));
        if (request.columnIds().size() != columns.size() || !byId.keySet().containsAll(request.columnIds())) {
            throw new ResourceNotFoundException("COLUMN_NOT_FOUND",
                    "columnIds must be a permutation of the board's columns");
        }
        for (int i = 0; i < request.columnIds().size(); i++) {
            byId.get(request.columnIds().get(i)).setPosition(i);
        }
        columnRepository.saveAll(columns);
        return columns.stream()
                .sorted(Comparator.comparing(BoardColumn::getPosition))
                .map(ColumnResponse::from)
                .toList();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Board requireBoard(Long userId, Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("BOARD_NOT_FOUND", "Board not found"));
        access.requireMembership(userId, board.getProjectId());
        return board;
    }

    private BoardColumn requireColumn(Long userId, Long columnId) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("COLUMN_NOT_FOUND", "Column not found"));
        requireBoard(userId, column.getBoardId());
        return column;
    }
}
