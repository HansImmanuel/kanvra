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
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.kafka.event.DomainEvent;
import com.kanvra.kafka.event.KafkaEventTypes;
import com.kanvra.outbox.EventPublisher;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.model.Task;
import com.kanvra.task.repository.TaskRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task comments (docs/SPEC.md §8). Only the comment author may edit/delete;
 * deletion is soft (sets {@code deleted_at}). Every mutation writes a
 * {@code comment.*} domain event to the transactional outbox in the same
 * transaction (ADR-005), feeding the activity/notification/realtime consumers.
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final BoardColumnRepository columnRepository;
    private final BoardRepository boardRepository;
    private final ProjectAccessService access;
    private final EventPublisher eventPublisher;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, TaskRepository taskRepository,
                          BoardColumnRepository columnRepository, BoardRepository boardRepository,
                          ProjectAccessService access, EventPublisher eventPublisher,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.columnRepository = columnRepository;
        this.boardRepository = boardRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
    }

    @Transactional
    public CommentResponse create(Long userId, Long taskId, CreateCommentRequest request) {
        Task task = requireTask(userId, taskId);
        Comment comment = new Comment();
        comment.setTaskId(taskId);
        comment.setAuthorId(userId);
        comment.setContent(request.content().trim());
        Comment saved = commentRepository.save(comment);

        eventPublisher.publish(commentEvent(KafkaEventTypes.COMMENT_CREATED, userId, projectIdOf(task), task, saved));

        User author = userRepository.findById(userId).orElse(null);
        return CommentResponse.from(saved, author);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> list(Long userId, Long taskId, Pageable pageable) {
        requireTask(userId, taskId);
        Page<Comment> comments = commentRepository.findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId, pageable);
        // Batch-load authors once (per distinct author, not per row) to avoid an
        // N+1 across the page. Missing users degrade to an "Unknown" author.
        Map<Long, User> authors = new HashMap<>();
        for (Comment comment : comments.getContent()) {
            authors.putIfAbsent(comment.getAuthorId(), userRepository.findById(comment.getAuthorId()).orElse(null));
        }
        return comments.map(c -> CommentResponse.from(c, authors.get(c.getAuthorId())));
    }

    @Transactional
    public CommentResponse update(Long userId, Long commentId, UpdateCommentRequest request) {
        Comment comment = requireAuthorableComment(userId, commentId);
        Task task = projectOfComment(comment);
        comment.setContent(request.content().trim());

        eventPublisher.publish(commentEvent(KafkaEventTypes.COMMENT_UPDATED, userId, projectIdOf(task), task, comment));

        User author = userRepository.findById(userId).orElse(null);
        return CommentResponse.from(comment, author);
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        Comment comment = requireAuthorableComment(userId, commentId);
        Task task = projectOfComment(comment);
        comment.setDeletedAt(Instant.now());

        eventPublisher.publish(commentEvent(KafkaEventTypes.COMMENT_DELETED, userId, projectIdOf(task), task, comment));
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    /** Requires project membership for the task's project and returns the task. */
    private Task requireTask(Long userId, Long taskId) {
        Task task = taskRepository.findActiveById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found"));
        access.requireMembership(userId, projectIdOf(task));
        return task;
    }

    /** Requires the user be a member AND the comment author, returns the comment. */
    private Comment requireAuthorableComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("COMMENT_NOT_FOUND", "Comment not found"));
        requireTask(userId, comment.getTaskId()); // membership check (and task existence)
        if (!comment.getAuthorId().equals(userId)) {
            throw new ForbiddenOperationException("Only the comment author can modify this comment");
        }
        return comment;
    }

    /** Loads the parent task of a comment (membership already checked by caller). */
    private Task projectOfComment(Comment comment) {
        return taskRepository.findActiveById(comment.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found"));
    }

    private Long projectIdOf(Task task) {
        return projectIdOf(task.getColumnId());
    }

    private Long projectIdOf(Long columnId) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("COLUMN_NOT_FOUND", "Column not found"));
        Board board = boardRepository.findById(column.getBoardId())
                .orElseThrow(() -> new ResourceNotFoundException("BOARD_NOT_FOUND", "Board not found"));
        return board.getProjectId();
    }

    private DomainEvent<Map<String, Object>> commentEvent(String type, Long userId, Long projectId,
                                                          Task task, Comment comment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", comment.getId());
        payload.put("taskId", task.getId());
        payload.put("taskTitle", task.getTitle());
        // assigneeId lets the Notification Consumer decide the recipient without
        // querying PostgreSQL (TECH_DOC.md §11 denormalized-payload decision).
        payload.put("assigneeId", task.getAssigneeId());
        payload.put("commentAuthorId", userId);
        payload.put("content", comment.getContent());
        payload.put("actorName", actorName(userId));
        return DomainEvent.of(type, userId, projectId, KafkaEventTypes.AGGREGATE_COMMENT, comment.getId(), payload);
    }

    private String actorName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse("Someone");
    }
}