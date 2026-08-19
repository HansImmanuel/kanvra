package com.kanvra.project.dto;

import com.kanvra.project.model.Label;

/**
 * Label representation (docs/SPEC.md §9).
 */
public record LabelResponse(Long id, Long projectId, String name, String color) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(label.getId(), label.getProjectId(), label.getName(), label.getColor());
    }
}
