package com.kanvra.comment.service;

import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.comment.dto.CommentResponse;
import com.kanvra.comment.dto.CreateCommentRequest;
import com.kanvra.comment.dto.UpdateCommentRequest;
import com.kanvra.comment.model.Comment;
import com.kanvra.comment.repository.CommentRepository;
import com.kanvra.common.error.ForbiddenOperationException;
import com.kanvra.kafka.event.DomainEvent;
import com.kanvra.kafka.event.KafkaEventTypes;
import com.kanvra.outbox.EventPublisher;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.model.Task;
import com.kanvra.task.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CommentService behavior (docs/SPEC.md §8): membership + author-only rules,
 * soft delete, and the comment.* outbox events carrying denormalized task
 * context for the consumers.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private BoardColumnRepository columnRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private ProjectAccessService access;
    @Mock private EventPublisher eventPublisher;
    @Mock private UserRepository userRepository;

    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, taskRepository, columnRepository, boardRepository,
                access, eventPublisher, userRepository);
    }

    private Task taskFixture(Long projectId) {
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", 7L);
        task.setColumnId(1L);
        task.setTitle("Fix auth");

        BoardColumn column = new BoardColumn();
        column.setBoardId(2L);
        Board board = new Board();
        board.setProjectId(projectId);
        when(columnRepository.findById(1L)).thenReturn(Optional.of(column));
        when(boardRepository.findById(2L)).thenReturn(Optional.of(board));
        return task;
    }

    private Comment commentFixture(Long authorId, Long id) {
        Comment comment = new Comment();
        ReflectionTestUtils.setField(comment, "id", id);
        comment.setTaskId(7L);
        comment.setAuthorId(authorId);
        comment.setContent("original");
        return comment;
    }

    @Test
    void createPublishesCommentCreatedWithTaskContext() {
        Task task = taskFixture(3L);
        when(taskRepository.findActiveById(7L)).thenReturn(Optional.of(task));
        User author = new User();
        author.setName("Hans");
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 42L);
            return c;
        });

        CommentResponse result = service.create(1L, 7L, new CreateCommentRequest("  Please verify  "));

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.content()).isEqualTo("Please verify");
        assertThat(result.author().name()).isEqualTo("Hans");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass((Class) DomainEvent.class);
        verify(eventPublisher).publish(captor.capture());
        DomainEvent<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(KafkaEventTypes.COMMENT_CREATED);
        assertThat(event.projectId()).isEqualTo(3L);
        assertThat(event.aggregateType()).isEqualTo(KafkaEventTypes.AGGREGATE_COMMENT);
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) event.payload();
        assertThat(payload).containsEntry("taskId", 7L)
                .containsEntry("taskTitle", "Fix auth")
                .containsEntry("actorName", "Hans");
    }

    @Test
    void listReturnsPageWithAuthorNames() {
        Task task = taskFixture(3L);
        when(taskRepository.findActiveById(7L)).thenReturn(Optional.of(task));
        Comment comment = commentFixture(1L, 42L);
        when(commentRepository.findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        User author = new User();
        author.setName("Hans");
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        var page = service.list(1L, 7L, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).author().name()).isEqualTo("Hans");
    }

    @Test
    void updateByNonAuthorIsForbidden() {
        when(commentRepository.findByIdAndDeletedAtIsNull(42L))
                .thenReturn(Optional.of(commentFixture(2L, 42L)));
        Task task = taskFixture(3L);
        when(taskRepository.findActiveById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.update(1L, 42L, new UpdateCommentRequest("nope")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void deleteSoftDeletesAndPublishesCommentDeleted() {
        Comment fixture = commentFixture(1L, 42L);
        when(commentRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(fixture));
        Task task = taskFixture(3L);
        when(taskRepository.findActiveById(7L)).thenReturn(Optional.of(task));
        User author = new User();
        author.setName("Hans");
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        service.delete(1L, 42L);

        assertThat(fixture.getDeletedAt()).isNotNull(); // soft delete marker set
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass((Class) DomainEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(KafkaEventTypes.COMMENT_DELETED);
    }
}