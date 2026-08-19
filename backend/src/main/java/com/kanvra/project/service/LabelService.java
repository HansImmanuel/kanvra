package com.kanvra.project.service;

import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.project.dto.LabelRequest;
import com.kanvra.project.dto.LabelResponse;
import com.kanvra.project.model.Label;
import com.kanvra.project.repository.LabelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project labels (docs/SPEC.md §9). Labels are hard-deleted.
 */
@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final ProjectAccessService access;

    public LabelService(LabelRepository labelRepository, ProjectAccessService access) {
        this.labelRepository = labelRepository;
        this.access = access;
    }

    @Transactional
    public LabelResponse create(Long userId, Long projectId, LabelRequest request) {
        access.requireMembership(userId, projectId);
        Label label = new Label();
        label.setProjectId(projectId);
        label.setName(request.name().trim());
        label.setColor(request.color());
        return LabelResponse.from(labelRepository.save(label));
    }

    @Transactional
    public LabelResponse update(Long userId, Long labelId, LabelRequest request) {
        Label label = requireLabel(userId, labelId);
        label.setName(request.name().trim());
        label.setColor(request.color());
        return LabelResponse.from(label);
    }

    @Transactional
    public void delete(Long userId, Long labelId) {
        requireLabel(userId, labelId);
        labelRepository.deleteById(labelId);
    }

    private Label requireLabel(Long userId, Long labelId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("LABEL_NOT_FOUND", "Label not found"));
        access.requireMembership(userId, label.getProjectId());
        return label;
    }
}
