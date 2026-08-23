package com.kanvra.board.service;

import com.kanvra.board.dto.ColumnResponse;
import com.kanvra.board.dto.ReorderColumnsRequest;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.project.model.Project;
import com.kanvra.task.dto.TaskQueryService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Board/column rules (docs/SPEC.md §5-§6), including the column-reorder
 * row-locking behavior that prevents concurrent reorders from interleaving
 * position writes (same canonical-ordering rule as task moves).
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock private BoardRepository boardRepository;
    @Mock private BoardColumnRepository columnRepository;
    @Mock private ProjectAccessService access;
    @Mock private TaskQueryService taskQueryService;
    @Mock private TaskRepository taskRepository;

    private Board board(long id, long projectId) {
        Board board = new Board();
        ReflectionTestUtils.setField(board, "id", id);
        board.setProjectId(projectId);
        return board;
    }

    private BoardColumn column(long id, long boardId, int position, String name) {
        BoardColumn c = new BoardColumn();
        ReflectionTestUtils.setField(c, "id", id);
        c.setBoardId(boardId);
        c.setName(name);
        c.setPosition(position);
        return c;
    }

    @Test
    void reorderColumnsLocksAllColumnsInCanonicalOrderAndPermutes() {
        Board board = board(10L, 1L);
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(access.requireMembership(7L, 1L)).thenReturn(new Project());

        BoardColumn todo = column(1L, 10L, 0, "TODO");
        BoardColumn progress = column(2L, 10L, 1, "IN PROGRESS");
        BoardColumn done = column(3L, 10L, 2, "DONE");
        when(columnRepository.lockColumnsByBoardId(10L)).thenReturn(List.of(todo, progress, done));

        List<ColumnResponse> result = new BoardService(boardRepository, columnRepository, access,
                taskQueryService, taskRepository).reorderColumns(7L, 10L,
                new ReorderColumnsRequest(List.of(3L, 1L, 2L)));

        // The reorder must go through the pessimistic canonical-order lock so two
        // concurrent reorders serialize instead of writing interleaved positions.
        verify(columnRepository).lockColumnsByBoardId(10L);
        assertThat(done.getPosition()).isEqualTo(0);
        assertThat(todo.getPosition()).isEqualTo(1);
        assertThat(progress.getPosition()).isEqualTo(2);
        assertThat(result).extracting(ColumnResponse::id).containsExactly(3L, 1L, 2L);
    }

    @Test
    void reorderColumnsRejectsNonPermutation() {
        Board board = board(10L, 1L);
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(access.requireMembership(7L, 1L)).thenReturn(new Project());

        BoardColumn todo = column(1L, 10L, 0, "TODO");
        BoardColumn progress = column(2L, 10L, 1, "IN PROGRESS");
        when(columnRepository.lockColumnsByBoardId(10L)).thenReturn(List.of(todo, progress));

        assertThatThrownBy(() -> new BoardService(boardRepository, columnRepository, access,
                taskQueryService, taskRepository).reorderColumns(7L, 10L,
                new ReorderColumnsRequest(List.of(1L, 99L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}