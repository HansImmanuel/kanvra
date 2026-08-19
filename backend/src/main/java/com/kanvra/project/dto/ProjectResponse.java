package com.kanvra.project.dto;

import com.kanvra.project.model.Project;
import java.time.Instant;

/**
 * Project representation returned by the Project API (docs/SPEC.md §4).
 */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        String status,
        Long ownerId,
        Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getOwnerId(),
                project.getCreatedAt());
    }
}
