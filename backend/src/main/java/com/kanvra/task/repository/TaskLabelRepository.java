package com.kanvra.task.repository;

import com.kanvra.task.model.TaskLabel;
import com.kanvra.task.model.TaskLabelId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, TaskLabelId> {

    List<TaskLabel> findByIdTaskId(Long taskId);

    void deleteByIdTaskId(Long taskId);
}
