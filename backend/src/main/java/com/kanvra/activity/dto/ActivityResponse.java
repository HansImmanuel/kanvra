package com.kanvra.activity.dto;

import com.kanvra.activity.model.Activity;
import java.time.Instant;

/**
 * Activity feed record (docs/SPEC.md §11).
 */
public record ActivityResponse(Long id, Long projectId, Long actorId, String type, String message,
                               Instant createdAt) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getProjectId(), activity.getActorId(),
                activity.getType(), activity.getMessage(), activity.getCreatedAt());
    }
}
