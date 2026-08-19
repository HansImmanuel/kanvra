package com.kanvra.task.controller;

import com.kanvra.common.security.CurrentUser;
import com.kanvra.task.dto.CreateTaskRequest;
import com.kanvra.task.dto.MoveTaskRequest;
import com.kanvra.task.dto.TaskResponse;
import com.kanvra.task.dto.UpdateTaskRequest;
import com.kanvra.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task API (docs/SPEC.md §7). Task creation is idempotent via the
 * {@code Idempotency-Key} header (ADR-008).
 */
@RestController
public class TaskController {

    private final TaskService taskService;
    private final CurrentUser currentUser;

    public TaskController(TaskService taskService, CurrentUser currentUser) {
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/columns/{columnId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable Long columnId,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(currentUser.require().id(), columnId, idempotencyKey, request);
    }

    @GetMapping("/api/v1/tasks/{taskId}")
    public TaskResponse get(@PathVariable Long taskId) {
        return taskService.get(currentUser.require().id(), taskId);
    }

    @PatchMapping("/api/v1/tasks/{taskId}")
    public TaskResponse update(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(currentUser.require().id(), taskId, request);
    }

    @DeleteMapping("/api/v1/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long taskId) {
        taskService.delete(currentUser.require().id(), taskId);
    }

    @PostMapping("/api/v1/tasks/{taskId}/move")
    public TaskResponse move(@PathVariable Long taskId, @Valid @RequestBody MoveTaskRequest request) {
        return taskService.move(currentUser.require().id(), taskId, request);
    }
}
