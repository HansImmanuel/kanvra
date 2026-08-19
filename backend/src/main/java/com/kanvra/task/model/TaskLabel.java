package com.kanvra.task.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Many-to-many link between tasks and project labels (docs/SPEC.md §7, §9).
 */
@Entity
@Table(name = "task_labels")
public class TaskLabel {

    @EmbeddedId
    private TaskLabelId id;

    protected TaskLabel() {
    }

    public TaskLabel(TaskLabelId id) {
        this.id = id;
    }

    public TaskLabelId getId() {
        return id;
    }
}
