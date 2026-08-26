package com.kanvra.analytics.service;

import com.kanvra.analytics.dto.ProjectAnalyticsResponse;
import com.kanvra.analytics.repository.ProjectAnalyticsRepository;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.common.error.ForbiddenOperationException;
import com.kanvra.project.model.Project;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * AnalyticsService reads (docs/SPEC.md §12.5): membership-gated; zero counters
 * before any event is consumed; cards-per-column derived live from the board
 * state (soft-deleted tasks excluded).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private ProjectAnalyticsRepository analyticsRepository;
    @Mock private ProjectAccessService access;
    @Mock private BoardRepository boardRepository;
    @Mock private BoardColumnRepository columnRepository;
    @Mock private TaskRepository taskRepository;

    private AnalyticsService service() {
        return new AnalyticsService(analyticsRepository, access, boardRepository, columnRepository, taskRepository);
    }

    private Board board(long id) {
        Board b = new Board();
        b.setName("Sprint");
        b.setProjectId(1L);
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    @Test
    void nonMemberIsRejected() {
        when(access.requireMembership(7L, 1L)).thenThrow(new ForbiddenOperationException("no access"));

        assertThatThrownBy(() -> service().get(7L, 1L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void returnsZeroCountersBeforeAnyEventAndLiveCardsPerColumn() {
        when(access.requireMembership(7L, 1L)).thenReturn(new Project());
        when(analyticsRepository.findById(1L)).thenReturn(Optional.empty());

        Board b = board(10L);
        when(boardRepository.findByProjectId(1L)).thenReturn(List.of(b));

        BoardColumn todo = column(101L, 10L, "TODO", 0);
        BoardColumn done = column(102L, 10L, "DONE", 2);
        when(columnRepository.findByBoardIdOrderByPositionAsc(10L)).thenReturn(List.of(todo, done));

        when(taskRepository.countByColumnIdInGrouped(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{101L, 3L}));

        ProjectAnalyticsResponse result = service().get(7L, 1L);

        assertThat(result.projectId()).isEqualTo(1L);
        assertThat(result.counters().tasksCreated()).isZero();
        assertThat(result.cardsPerColumn()).hasSize(2);
        assertThat(result.cardsPerColumn().get(0).columnName()).isEqualTo("TODO");
        assertThat(result.cardsPerColumn().get(0).count()).isEqualTo(3L);
        assertThat(result.cardsPerColumn().get(1).columnName()).isEqualTo("DONE");
        assertThat(result.cardsPerColumn().get(1).count()).isZero();
    }

    private BoardColumn column(long id, long boardId, String name, int position) {
        BoardColumn c = new BoardColumn();
        c.setName(name);
        c.setPosition(position);
        c.setBoardId(boardId);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}