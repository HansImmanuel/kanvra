package com.kanvra.task.repository;

import com.kanvra.task.model.Task;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(Long columnId);

    List<Task> findByColumnIdInAndDeletedAtIsNull(List<Long> columnIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.columnId = :columnId and t.deletedAt is null order by t.position asc")
    List<Task> lockTasksInColumn(@Param("columnId") Long columnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> lockById(@Param("id") Long id);

    @Query("select t from Task t where t.id = :id and t.deletedAt is null")
    Optional<Task> findActiveById(@Param("id") Long id);

    /** Per-column active-task counts — used by the analytics endpoint (SPEC §12.5). */
    @Query("select t.columnId, count(t) from Task t "
            + "where t.columnId in :columnIds and t.deletedAt is null group by t.columnId")
    List<Object[]> countByColumnIdInGrouped(@Param("columnIds") List<Long> columnIds);
}
