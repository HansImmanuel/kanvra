package com.kanvra.realtime.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbound realtime board message (docs/SPEC.md §15.5). The domain event's
 * denormalized payload is flattened into the message (no extra client reads), and
 * the UI mapping + {@code eventId} let the frontend reconcile optimistic local echo
 * (SPEC §15.3).
 */
public record RealtimeMessage(String type, String eventId, Long projectId, Map<String, Object> payload) {

    public static RealtimeMessage from(String eventType, String eventId, Long projectId, JsonNode payload) {
        Map<String, Object> flat = new LinkedHashMap<>();
        payload.properties().forEach(e -> flat.put(e.getKey(), e.getValue()));
        return new RealtimeMessage(toRealtimeType(eventType), eventId, projectId, flat);
    }

    private static String toRealtimeType(String eventType) {
        return switch (eventType) {
            case "task.created" -> "TASK_CREATED";
            case "task.updated" -> "TASK_UPDATED";
            case "task.deleted" -> "TASK_DELETED";
            case "task.moved" -> "TASK_MOVED";
            case "task.assigned" -> "TASK_ASSIGNED";
            case "task.unassigned" -> "TASK_UNASSIGNED";
            case "task.completed" -> "TASK_COMPLETED";
            case "comment.created" -> "COMMENT_CREATED";
            case "comment.updated" -> "COMMENT_UPDATED";
            case "comment.deleted" -> "COMMENT_DELETED";
            case "project.created" -> "PROJECT_CREATED";
            case "project.member_added" -> "PROJECT_MEMBER_ADDED";
            case "project.archived" -> "PROJECT_ARCHIVED";
            default -> eventType.replace('.', '_').toUpperCase();
        };
    }
}