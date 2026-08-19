package com.kanvra.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.common.error.OptimisticLockException;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.common.error.ValidationException;
import com.kanvra.common.error.ValidationFieldError;
import com.kanvra.kafka.event.DomainEvent;
import com.kanvra.kafka.event.KafkaEventTypes;
import com.kanvra.outbox.EventPublisher;
import com.kanvra.project.model.Label;
import com.kanvra.project.repository.LabelRepository;
import com.kanvra.project.repository.ProjectMemberRepository;
import com.kanvra.project.service.ProjectAccessService;
import com.kanvra.task.dto.CreateTaskRequest;
import com.kanvra.task.dto.MoveTaskRequest;
import com.kanvra.task.dto.TaskResponse;
import com.kanvra.task.dto.UpdateTaskRequest;
import com.kanvra.task.model.IdempotencyKey;
import com.kanvra.task.model.Task;
import com.kanvra.task.model.TaskLabel;
import com.kanvra.task.model.TaskLabelId;
import com.kanvra.task.repository.IdempotencyKeyRepository;
import com.kanvra.task.repository.TaskLabelRepository;
import com.kanvra.task.repository.TaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task operations (docs/SPEC.md §7): idempotent creation, optimistic-locked
 * updates, row-locked moves with dense positions, and soft deletion. Every
 * mutation writes its domain event to the transactional outbox in the same
 * transaction (ADR-005, TECH_DOC.md §8).
 */
@Service
public class TaskService {

    private static final String IDEMPOTENCY_RESOURCE_TASK = "TASK";
    private static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);

    private final TaskRepository taskRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final LabelRepository labelRepository;
    private final BoardColumnRepository columnRepository;
    private final BoardRepository boardRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectAccessService access;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, TaskLabelRepository taskLabelRepository,
                       LabelRepository labelRepository, BoardColumnRepository columnRepository,
                       BoardRepository boardRepository, ProjectMemberRepository memberRepository,
                       ProjectAccessService access, IdempotencyKeyRepository idempotencyRepository,
                       EventPublisher eventPublisher, ObjectMapper objectMapper, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.labelRepository = labelRepository;
        this.columnRepository = columnRepository;
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.access = access;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse create(Long userId, Long columnId, String idempotencyKey, CreateTaskRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            TaskResponse cached = replayIdempotentCreate(userId, idempotencyKey);
            if (cached != null) {
                return cached;
            }
        }

        BoardColumn column = requireColumnAccess(userId, columnId);
        Long projectId = projectIdOf(column.getBoardId());

        validateAssignee(projectId, request.assigneeId());
        List<Label> labels = validateLabels(projectId, request.labelIds());

        int position = taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(columnId).size();

        Task task = new Task();
        task.setColumnId(columnId);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setAssigneeId(request.assigneeId());
        task.setDueDate(request.dueDate());
        task.setPosition(position);
        taskRepository.save(task);

        linkLabels(task.getId(), labels);

        DomainEvent<Map<String, Object>> event = createEvent(
                KafkaEventTypes.TASK_CREATED, userId, projectId, task, actorName(userId),
                Map.of("taskId", task.getId(), "taskTitle", task.getTitle(),
                        "columnId", columnId, "columnName", column.getName()));
        eventPublisher.publish(event);

        TaskResponse response = TaskResponse.from(task).withEventId(event.eventId());
        storeIdempotencyKey(userId, idempotencyKey, task.getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long userId, Long taskId) {
        Task task = requireTaskAccess(userId, taskId);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest request) {
        Task task = requireTaskAccess(userId, taskId);
        if (!task.getVersion().equals(request.version())) {
            throw new OptimisticLockException("Task was modified by someone else; refresh and retry");
        }

        Long previousAssignee = task.getAssigneeId();
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setAssigneeId(request.assigneeId());
        task.setDueDate(request.dueDate());
        replaceLabels(taskId, request.labelIds());

        Long projectId = projectIdOf(boardIdOf(task.getColumnId()));
        String actorName = actorName(userId);

        DomainEvent<Map<String, Object>> event = createEvent(
                KafkaEventTypes.TASK_UPDATED, userId, projectId, task, actorName,
                Map.of("taskId", task.getId(), "taskTitle", task.getTitle(), "columnId", task.getColumnId()));
        eventPublisher.publish(event);

        if (request.assigneeId() != null && !request.assigneeId().equals(previousAssignee)) {
            eventPublisher.publish(createEvent(
                    KafkaEventTypes.TASK_ASSIGNED, userId, projectId, task, actorName,
                    Map.of("taskId", task.getId(), "taskTitle", task.getTitle(), "assigneeId", request.assigneeId())));
        } else if (request.assigneeId() == null && previousAssignee != null) {
            eventPublisher.publish(createEvent(
                    KafkaEventTypes.TASK_UNASSIGNED, userId, projectId, task, actorName,
                    Map.of("taskId", task.getId(), "taskTitle", task.getTitle())));
        }

        return TaskResponse.from(task).withEventId(event.eventId());
    }

    @Transactional
    public TaskResponse move(Long userId, Long taskId, MoveTaskRequest request) {
        Task task = taskRepository.lockById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found"));
        BoardColumn from = requireColumnAccess(userId, task.getColumnId());
        BoardColumn to = requireColumnAccess(userId, request.targetColumnId());
        if (!from.getBoardId().equals(to.getBoardId())) {
            throw new ValidationException(List.of(
                    new ValidationFieldError("targetColumnId", "Column must belong to the same board")));
        }
        if (!task.getVersion().equals(request.version())) {
            throw new OptimisticLockException("Task was modified by someone else; refresh and retry");
        }

        Long projectId = projectIdOf(from.getBoardId());

        if (from.getId().equals(to.getId())) {
            List<Task> columnTasks = taskRepository.lockTasksInColumn(from.getId());
            columnTasks.removeIf(t -> t.getId().equals(taskId));
            int position = clamp(request.position(), columnTasks.size());
            task.setPosition(position);
            columnTasks.add(position, task);
            reassignPositions(columnTasks);
            taskRepository.saveAll(columnTasks);
        } else {
            List<Task> sourceTasks = taskRepository.lockTasksInColumn(from.getId());
            sourceTasks.removeIf(t -> t.getId().equals(taskId));
            reassignPositions(sourceTasks);
            taskRepository.saveAll(sourceTasks);

            List<Task> targetTasks = taskRepository.lockTasksInColumn(to.getId());
            int position = clamp(request.position(), targetTasks.size());
            task.setColumnId(to.getId());
            task.setPosition(position);
            targetTasks.add(position, task);
            reassignPositions(targetTasks);
            taskRepository.saveAll(targetTasks);
        }

        boolean completed = isDone(to) && !isDone(from);
        String actorName = actorName(userId);

        DomainEvent<Map<String, Object>> event = createEvent(
                KafkaEventTypes.TASK_MOVED, userId, projectId, task, actorName,
                Map.of("taskId", task.getId(), "taskTitle", task.getTitle(),
                        "fromColumnId", from.getId(), "fromColumnName", from.getName(),
                        "toColumnId", to.getId(), "toColumnName", to.getName(),
                        "position", task.getPosition()));
        eventPublisher.publish(event);

        if (completed) {
            eventPublisher.publish(createEvent(
                    KafkaEventTypes.TASK_COMPLETED, userId, projectId, task, actorName,
                    Map.of("taskId", task.getId(), "taskTitle", task.getTitle(),
                            "columnId", to.getId(), "columnName", to.getName())));
        }

        return TaskResponse.from(task).withEventId(event.eventId());
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = requireTaskAccess(userId, taskId);
        task.setDeletedAt(Instant.now());
        Long projectId = projectIdOf(boardIdOf(task.getColumnId()));

        DomainEvent<Map<String, Object>> event = createEvent(
                KafkaEventTypes.TASK_DELETED, userId, projectId, task, actorName(userId),
                Map.of("taskId", task.getId(), "taskTitle", task.getTitle(), "columnId", task.getColumnId()));
        eventPublisher.publish(event);
    }


    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private TaskResponse replayIdempotentCreate(Long userId, String key) {
        return idempotencyRepository
                .findByUserIdAndIdempotencyKeyAndResourceType(userId, key, IDEMPOTENCY_RESOURCE_TASK)
                .filter(rec -> rec.getExpiresAt().isAfter(Instant.now()))
                .map(rec -> objectMapper.convertValue(rec.getResponseBody(), TaskResponse.class))
                .orElse(null);
    }

    private void storeIdempotencyKey(Long userId, String key, Long resourceId, TaskResponse response) {
        if (key == null || key.isBlank()) {
            return;
        }
        // Replace any expired row so the unique constraint doesn't block re-use
        // after the 24h dedup window.
        idempotencyRepository
                .findByUserIdAndIdempotencyKeyAndResourceType(userId, key, IDEMPOTENCY_RESOURCE_TASK)
                .filter(rec -> !rec.getExpiresAt().isAfter(Instant.now()))
                .ifPresent(idempotencyRepository::delete);

        IdempotencyKey rec = new IdempotencyKey();
        rec.setUserId(userId);
        rec.setIdempotencyKey(key);
        rec.setResourceType(IDEMPOTENCY_RESOURCE_TASK);
        rec.setResourceId(resourceId);
        rec.setResponseBody(objectMapper.valueToTree(response));
        rec.setStatusCode(201);
        rec.setExpiresAt(Instant.now().plus(IDEMPOTENCY_WINDOW));
        idempotencyRepository.save(rec);
    }

    private void validateAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return;
        }
        boolean member = memberRepository.existsByIdProjectIdAndIdUserId(projectId, assigneeId);
        if (!member) {
            throw new ValidationException(List.of(
                    new ValidationFieldError("assigneeId", "Assignee must be a member of the project")));
        }
    }

    private List<Label> validateLabels(Long projectId, List<Long> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>(labelIds);
        List<Label> labels = labelRepository.findAllById(ids);
        boolean allInProject = labels.size() == ids.size()
                && labels.stream().allMatch(l -> l.getProjectId().equals(projectId));
        if (!allInProject) {
            throw new ValidationException(List.of(
                    new ValidationFieldError("labelIds", "All labels must belong to this project")));
        }
        return labels;
    }

    private void linkLabels(Long taskId, List<Label> labels) {
        for (Label label : labels) {
            taskLabelRepository.save(new TaskLabel(new TaskLabelId(taskId, label.getId())));
        }
    }

    private void replaceLabels(Long taskId, List<Long> labelIds) {
        taskLabelRepository.deleteByIdTaskId(taskId);
        if (labelIds == null || labelIds.isEmpty()) {
            return;
        }
        for (Long labelId : labelIds) {
            taskLabelRepository.save(new TaskLabel(new TaskLabelId(taskId, labelId)));
        }
    }

    private DomainEvent<Map<String, Object>> createEvent(String type, Long userId, Long projectId, Task task,
                                                         String actorName, Map<String, Object> payload) {
        Map<String, Object> enriched = new java.util.HashMap<>(payload);
        enriched.put("actorName", actorName);
        return DomainEvent.of(type, userId, projectId, KafkaEventTypes.AGGREGATE_TASK, task.getId(), enriched);
    }

    private String actorName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse("Someone");
    }

    private void reassignPositions(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
    }

    private int clamp(int position, int size) {
        return Math.max(0, Math.min(position, size));
    }

    private boolean isDone(BoardColumn column) {
        return "done".equalsIgnoreCase(column.getName());
    }

    private Long boardIdOf(Long columnId) {
        return columnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("COLUMN_NOT_FOUND", "Column not found")).getBoardId();
    }

    private Long projectIdOf(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("BOARD_NOT_FOUND", "Board not found")).getProjectId();
    }

    private BoardColumn requireColumnAccess(Long userId, Long columnId) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("COLUMN_NOT_FOUND", "Column not found"));
        access.requireMembership(userId, projectIdOf(column.getBoardId()));
        return column;
    }

    private Task requireTaskAccess(Long userId, Long taskId) {
        Task task = taskRepository.findActiveById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found"));
        access.requireMembership(userId, projectIdOf(boardIdOf(task.getColumnId())));
        return task;
    }
}

