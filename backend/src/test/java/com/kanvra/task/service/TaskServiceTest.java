package com.kanvra.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.common.error.OptimisticLockException;
import com.kanvra.outbox.EventPublisher;
import com.kanvra.project.repository.LabelRepository;
import com.kanvra.project.repository.ProjectMemberRepository;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.dto.CreateTaskRequest;
import com.kanvra.task.dto.TaskResponse;
import com.kanvra.task.dto.UpdateTaskRequest;
import com.kanvra.task.model.IdempotencyKey;
import com.kanvra.task.model.Task;
import com.kanvra.task.repository.IdempotencyKeyRepository;
import com.kanvra.task.repository.TaskLabelRepository;
import com.kanvra.task.repository.TaskRepository;
import com.kanvra.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskLabelRepository taskLabelRepository;
    @Mock private LabelRepository labelRepository;
    @Mock private BoardColumnRepository columnRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private ProjectAccessService access;
    @Mock private IdempotencyKeyRepository idempotencyRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskLabelRepository, labelRepository, columnRepository,
                boardRepository, memberRepository, access, idempotencyRepository, eventPublisher, objectMapper,
                userRepository);
    }

    @Test
    void createReplaysCachedIdempotentResponseWithoutDatabaseWork() {
        TaskResponse original = new TaskResponse(99L, 1L, "Original", null, "HIGH", null, null, 0, 0,
                Instant.now(), UUID.randomUUID());
        IdempotencyKey hit = new IdempotencyKey();
        hit.setResponseBody(objectMapper.valueToTree(original));
        hit.setExpiresAt(Instant.now().plusSeconds(60));

        when(idempotencyRepository.findByUserIdAndIdempotencyKeyAndResourceType(1L, "key-1", "TASK"))
                .thenReturn(Optional.of(hit));

        TaskResponse result = service.create(1L, 1L, "key-1",
                new CreateTaskRequest("Original", null, "HIGH", null, null, null));

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.eventId()).isEqualTo(original.eventId());
        verifyNoInteractions(columnRepository, taskRepository);
    }

    @Test
    void updateRejectsStaleVersion() {
        Task task = new Task();
        task.setColumnId(1L);
        task.setTitle("Current");
        task.setVersion(5);

        BoardColumn column = new BoardColumn();
        column.setBoardId(2L);
        Board board = new Board();
        board.setProjectId(3L);

        when(taskRepository.findActiveById(7L)).thenReturn(Optional.of(task));
        when(columnRepository.findById(1L)).thenReturn(Optional.of(column));
        when(boardRepository.findById(2L)).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> service.update(1L, 7L,
                new UpdateTaskRequest("Stale write", null, null, null, null, null, 4)))
                .isInstanceOf(OptimisticLockException.class);
    }
}
