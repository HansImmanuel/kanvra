package com.kanvra.notification.dto;

import com.kanvra.notification.model.Notification;
import java.time.Instant;

/**
 * Notification representation (docs/SPEC.md §12). {@code referenceType} tells
 * the frontend what {@code referenceId} points to (TASK | COMMENT | PROJECT).
 */
public record NotificationResponse(
        Long id,
        String type,
        Long referenceId,
        String referenceType,
        String message,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getReferenceId(), n.getReferenceType(),
                n.getMessage(), n.getReadAt(), n.getCreatedAt());
    }
}