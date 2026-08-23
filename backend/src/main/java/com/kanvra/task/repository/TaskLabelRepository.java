package com.kanvra.task.repository;

import com.kanvra.task.model.TaskLabel;
import com.kanvra.task.model.TaskLabelId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, TaskLabelId> {

    List<TaskLabel> findByIdTaskId(Long taskId);

    /**
     * Batched task-label link lookup so the nested board response (SPEC.md
     * Appendix A) loads labels in one query instead of one per task.
     */
    List<TaskLabel> findByIdTaskIdIn(Collection<Long> taskIds);

    void deleteByIdTaskId(Long taskId);

    void deleteByIdLabelId(Long labelId);
}
