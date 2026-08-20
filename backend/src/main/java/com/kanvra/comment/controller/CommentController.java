package com.kanvra.comment.controller;

import com.kanvra.comment.dto.CommentResponse;
import com.kanvra.comment.dto.CreateCommentRequest;
import com.kanvra.comment.dto.UpdateCommentRequest;
import com.kanvra.comment.service.CommentService;
import com.kanvra.common.api.ApiPage;
import com.kanvra.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comment API (docs/SPEC.md §8): create/list on a task, author-only edit/delete
 * on the comment itself.
 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;
    private final CurrentUser currentUser;

    public CommentController(CommentService commentService, CurrentUser currentUser) {
        this.commentService = commentService;
        this.currentUser = currentUser;
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable Long taskId, @Valid @RequestBody CreateCommentRequest request) {
        return commentService.create(currentUser.require().id(), taskId, request);
    }

    @GetMapping("/tasks/{taskId}/comments")
    public ApiPage<CommentResponse> list(@PathVariable Long taskId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiPage.from(commentService.list(currentUser.require().id(), taskId, PageRequest.of(page, size)));
    }

    @PatchMapping("/comments/{commentId}")
    public CommentResponse update(@PathVariable Long commentId, @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(currentUser.require().id(), commentId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long commentId) {
        commentService.delete(currentUser.require().id(), commentId);
    }
}