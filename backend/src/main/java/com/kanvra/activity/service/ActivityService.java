package com.kanvra.activity.service;

import com.kanvra.activity.dto.ActivityResponse;
import com.kanvra.activity.repository.ActivityRepository;
import com.kanvra.project.service.ProjectAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activity feed reads (docs/SPEC.md §11). Rows are written by the Activity
 * Consumer; this service only reads them, gated by project membership.
 */
@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ProjectAccessService access;

    public ActivityService(ActivityRepository activityRepository, ProjectAccessService access) {
        this.activityRepository = activityRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> list(Long userId, Long projectId, Pageable pageable) {
        access.requireMembership(userId, projectId);
        return activityRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(ActivityResponse::from);
    }
}
