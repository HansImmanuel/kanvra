package com.kanvra.comment.dto;

import com.kanvra.auth.model.User;
import com.kanvra.comment.model.Comment;
import java.time.Instant;

/**
 * Comment representation (docs/SPEC.md §8). {@code author} mirrors the
 * assignee shape used in the board response (SPEC.md Appendix A).
 */
public record CommentResponse(
        Long id,
        Long taskId,
        Author author,
        String content,
        Instant createdAt,
        Instant updatedAt) {

    public record Author(Long id, String name, String avatarUrl) {
    }

    public static CommentResponse from(Comment comment, User author) {
        Author authorRef = author != null
                ? new Author(author.getId(), author.getName(), author.getAvatarUrl())
                : new Author(comment.getAuthorId(), "Unknown", null);
        return new CommentResponse(comment.getId(), comment.getTaskId(), authorRef, comment.getContent(),
                comment.getCreatedAt(), comment.getUpdatedAt());
    }
}