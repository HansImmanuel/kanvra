package com.kanvra.comment.repository;

import com.kanvra.comment.model.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Active comments of a task in thread order (oldest first). */
    Page<Comment> findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long taskId, Pageable pageable);

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Active comment counts grouped by task id — a single batched query so the
     * nested board response (SPEC.md Appendix A) does not issue an N+1 of
     * per-task counting queries.
     */
    @Query("""
            select c.taskId, count(c) from Comment c
            where c.taskId in :taskIds and c.deletedAt is null
            group by c.taskId""")
    List<Object[]> countByTaskIdGrouped(@Param("taskIds") Collection<Long> taskIds);
}