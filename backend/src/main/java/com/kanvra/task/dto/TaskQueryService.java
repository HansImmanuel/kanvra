package com.kanvra.task.dto;

import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.comment.repository.CommentRepository;
import com.kanvra.project.model.Label;
import com.kanvra.project.repository.LabelRepository;
import com.kanvra.task.model.Task;
import com.kanvra.task.model.TaskLabel;
import com.kanvra.task.repository.TaskLabelRepository;
import com.kanvra.task.repository.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds task card data for board rendering with batched user/label lookups so
 * the nested board response avoids N+1 queries (docs/SPEC.md §18, Appendix A).
 */
@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final LabelRepository labelRepository;
    private final UserRepository userRepository;
    private final BoardColumnRepository columnRepository;
    private final CommentRepository commentRepository;

    public TaskQueryService(TaskRepository taskRepository, TaskLabelRepository taskLabelRepository,
                            LabelRepository labelRepository, UserRepository userRepository,
                            BoardColumnRepository columnRepository, CommentRepository commentRepository) {
        this.taskRepository = taskRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.labelRepository = labelRepository;
        this.userRepository = userRepository;
        this.columnRepository = columnRepository;
        this.commentRepository = commentRepository;
    }

    public Map<Long, List<TaskSummary>> summariesByColumn(List<Long> columnIds) {
        if (columnIds.isEmpty()) {
            return Map.of();
        }
        List<Task> tasks = taskRepository.findByColumnIdInAndDeletedAtIsNull(columnIds);

        Set<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());

        // Batched label links for ALL tasks in the board (one query, not one per task).
        List<TaskLabel> links = taskLabelRepository.findByIdTaskIdIn(taskIds);
        Set<Long> labelIds = links.stream().map(l -> l.getId().getLabelId()).collect(Collectors.toSet());
        Map<Long, Label> labelsById = labelRepository.findAllById(labelIds).stream()
                .collect(Collectors.toMap(Label::getId, Function.identity(), (a, b) -> a));

        Set<Long> assigneeIds = tasks.stream().map(Task::getAssigneeId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> usersById = userRepository.findAllById(assigneeIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        // One grouped COUNT query for all tasks (SPEC.md Appendix A commentCount).
        Map<Long, Long> commentCounts = commentRepository.countByTaskIdGrouped(taskIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1], (a, b) -> a));

        return tasks.stream()
                .collect(Collectors.groupingBy(Task::getColumnId,
                        Collectors.mapping(task -> toSummary(task, links, labelsById, usersById, commentCounts),
                                Collectors.toList())));
    }

    private TaskSummary toSummary(Task task, List<TaskLabel> links, Map<Long, Label> labelsById,
                                  Map<Long, User> usersById, Map<Long, Long> commentCounts) {
        List<TaskSummary.LabelInfo> labels = links.stream()
                .filter(l -> l.getId().getTaskId().equals(task.getId()))
                .map(l -> labelsById.get(l.getId().getLabelId()))
                .filter(java.util.Objects::nonNull)
                .map(l -> new TaskSummary.LabelInfo(l.getId(), l.getName(), l.getColor()))
                .toList();

        TaskSummary.AssigneeInfo assignee = null;
        if (task.getAssigneeId() != null) {
            User user = usersById.get(task.getAssigneeId());
            if (user != null) {
                assignee = new TaskSummary.AssigneeInfo(user.getId(), user.getName(), user.getAvatarUrl());
            }
        }

        return new TaskSummary(task.getId(), task.getTitle(), task.getPriority(), task.getPosition(),
                task.getVersion(), assignee, labels, task.getDueDate(), commentCounts.getOrDefault(task.getId(), 0L));
    }

    public List<BoardColumn> columnsOf(Long boardId) {
        return columnRepository.findByBoardIdOrderByPositionAsc(boardId);
    }
}
