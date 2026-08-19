package com.kanvra.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@code task_labels} (task_id, label_id).
 */
@Embeddable
public class TaskLabelId implements Serializable {

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "label_id")
    private Long labelId;

    protected TaskLabelId() {
    }

    public TaskLabelId(Long taskId, Long labelId) {
        this.taskId = taskId;
        this.labelId = labelId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getLabelId() {
        return labelId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaskLabelId that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId) && Objects.equals(labelId, that.labelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, labelId);
    }
}
