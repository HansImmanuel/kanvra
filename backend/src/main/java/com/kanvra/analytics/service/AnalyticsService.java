package com.kanvra.analytics.service;

import com.kanvra.analytics.dto.ProjectAnalyticsResponse;
import com.kanvra.analytics.dto.ProjectAnalyticsResponse.CardsPerColumn;
import com.kanvra.analytics.dto.ProjectAnalyticsResponse.ProjectCounters;
import com.kanvra.analytics.model.ProjectAnalytics;
import com.kanvra.analytics.repository.ProjectAnalyticsRepository;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.repository.TaskRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project analytics reads (docs/SPEC.md §12.5). Counters are written by the
 * Analytics Consumer; this service only reads them plus the authoritative
 * board state, gated by project membership.
 */
@Service
public class AnalyticsService {

    private final ProjectAnalyticsRepository analyticsRepository;
    private final ProjectAccessService access;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;

    public AnalyticsService(ProjectAnalyticsRepository analyticsRepository, ProjectAccessService access,
                            BoardRepository boardRepository, BoardColumnRepository columnRepository,
                            TaskRepository taskRepository) {
        this.analyticsRepository = analyticsRepository;
        this.access = access;
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public ProjectAnalyticsResponse get(Long userId, Long projectId) {
        access.requireMembership(userId, projectId);

        ProjectAnalytics row = analyticsRepository.findById(projectId).orElse(null);
        ProjectCounters counters = row != null ? ProjectCounters.from(row) : ProjectAnalyticsResponse.ZERO_COUNTERS;

        return new ProjectAnalyticsResponse(projectId, counters, cardsPerColumn(projectId));
    }

    /**
     * Live card counts per column, derived from the authoritative board state
     * (soft-deleted tasks excluded). A project may have several boards; every
     * column id is globally unique so a project-wide list is unambiguous.
     */
    private List<CardsPerColumn> cardsPerColumn(Long projectId) {
        List<Board> boards = boardRepository.findByProjectId(projectId).stream()
                .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                .sorted(Comparator.comparing(Board::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<BoardColumn> columns = boards.stream()
                .flatMap(b -> columnRepository.findByBoardIdOrderByPositionAsc(b.getId()).stream())
                .toList();
        if (columns.isEmpty()) {
            return List.of();
        }

        List<Long> columnIds = columns.stream().map(BoardColumn::getId).toList();
        Map<Long, Long> counts = taskRepository.countByColumnIdInGrouped(columnIds).stream()
                .collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));

        return columns.stream()
                .map(c -> new CardsPerColumn(c.getId(), c.getName(), c.getPosition(),
                        counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }
}