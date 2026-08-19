package com.kanvra.kafka.event;

/**
 * Domain event type names (docs/SPEC.md §13). All events share the single
 * {@code kanvra.domain-events} topic, differentiated by {@code eventType}.
 */
public final class KafkaEventTypes {

    public static final String TASK_CREATED = "task.created";
    public static final String TASK_UPDATED = "task.updated";
    public static final String TASK_DELETED = "task.deleted";
    public static final String TASK_MOVED = "task.moved";
    public static final String TASK_ASSIGNED = "task.assigned";
    public static final String TASK_UNASSIGNED = "task.unassigned";
    public static final String TASK_COMPLETED = "task.completed";
    public static final String PROJECT_CREATED = "project.created";
    public static final String PROJECT_MEMBER_ADDED = "project.member_added";
    public static final String PROJECT_ARCHIVED = "project.archived";

    public static final String AGGREGATE_TASK = "task";
    public static final String AGGREGATE_PROJECT = "project";
    public static final String AGGREGATE_COMMENT = "comment";

    private KafkaEventTypes() {
    }
}
